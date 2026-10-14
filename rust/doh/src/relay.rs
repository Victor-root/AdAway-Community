//! The relay between the VPN tunnel and a DNS over HTTPS server: to the tunnel, a datagram socket
//! like the ones connected to the plain DNS servers; on the other side, each query goes to the
//! server over one of a few kept-alive HTTPS connections. It stops once the tunnel closes its end.

use std::io::{self, BufReader};
use std::net::{IpAddr, SocketAddr, TcpStream};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::sync::mpsc::{self, Receiver};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use rustls::pki_types::{ServerName, TrustAnchor};
use rustls::{ClientConfig, ClientConnection, RootCertStore, StreamOwned};

use crate::http;
use crate::url::DohUrl;

/// How many queries can be on their way at once.
const CONNECTIONS: usize = 4;
const TIMEOUT: Duration = Duration::from_secs(5);
const BUFFER_SIZE: usize = 65_536;

type Connection = BufReader<StreamOwned<ClientConnection, TcpStream>>;

/// What every connection to the server needs.
struct Server {
    url: DohUrl,
    name: ServerName<'static>,
    addresses: Vec<IpAddr>,
    tls: Arc<ClientConfig>,
}

/// Starts the relay to the server at `url`, reached at one of `addresses`, and returns the end of
/// the tunnel, a `SOCK_SEQPACKET` socket.
pub fn start(url: DohUrl, addresses: Vec<IpAddr>) -> io::Result<OwnedFd> {
    start_with(
        url,
        addresses,
        tls_config(webpki_roots::TLS_SERVER_ROOTS.to_vec())?,
    )
}

fn start_with(url: DohUrl, addresses: Vec<IpAddr>, tls: ClientConfig) -> io::Result<OwnedFd> {
    if addresses.is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "no address for the server",
        ));
    }
    let name = ServerName::try_from(url.host.clone())
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "server name"))?;
    let server = Arc::new(Server {
        url,
        name,
        addresses,
        tls: Arc::new(tls),
    });
    let (tunnel_end, relay_end) = socket_pair()?;
    let relay_end = Arc::new(relay_end);
    let (queries, pending) = mpsc::channel::<Vec<u8>>();
    let pending = Arc::new(Mutex::new(pending));
    for _ in 0..CONNECTIONS {
        let (server, pending, relay_end) = (
            Arc::clone(&server),
            Arc::clone(&pending),
            Arc::clone(&relay_end),
        );
        thread::spawn(move || answer_queries(&server, &pending, &relay_end));
    }
    // Ends when the tunnel closes its end, which also ends the connections once idle.
    thread::spawn(move || {
        let mut buffer = vec![0u8; BUFFER_SIZE];
        while let Ok(Some(length)) = receive_query(&relay_end, &mut buffer) {
            if length > 0 && queries.send(buffer[..length].to_vec()).is_err() {
                break;
            }
        }
    });
    Ok(tunnel_end)
}

fn tls_config(roots: Vec<TrustAnchor<'static>>) -> io::Result<ClientConfig> {
    let roots = RootCertStore { roots };
    let mut config =
        ClientConfig::builder_with_provider(Arc::new(rustls::crypto::ring::default_provider()))
            .with_safe_default_protocol_versions()
            .map_err(io::Error::other)?
            .with_root_certificates(roots)
            .with_no_client_auth();
    config.alpn_protocols = vec![b"http/1.1".to_vec()];
    Ok(config)
}

/// Sends the queries one after the other over a connection, made again when it fails.
fn answer_queries(server: &Server, pending: &Mutex<Receiver<Vec<u8>>>, relay_end: &OwnedFd) {
    let mut connection: Option<Connection> = None;
    loop {
        let query = match pending.lock().map(|pending| pending.recv()) {
            Ok(Ok(query)) => query,
            _ => return,
        };
        // A kept-alive connection the server closed meanwhile fails at once: one more try.
        let answer = exchange(server, &mut connection, &query)
            .or_else(|_| exchange(server, &mut connection, &query));
        // An unanswered query is retried by the app itself.
        if let Ok(answer) = answer {
            let _ = send(relay_end, &answer);
        }
    }
}

fn exchange(
    server: &Server,
    connection: &mut Option<Connection>,
    query: &[u8],
) -> io::Result<Vec<u8>> {
    let stream = match connection {
        Some(stream) => stream,
        None => connection.insert(connect(server)?),
    };
    let result = http::write_query(stream.get_mut(), &server.url, query)
        .and_then(|()| http::read_answer(stream));
    match result {
        Ok(answer) => {
            if !answer.keep_alive {
                *connection = None;
            }
            Ok(answer.message)
        }
        Err(error) => {
            *connection = None;
            Err(error)
        }
    }
}

fn connect(server: &Server) -> io::Result<Connection> {
    let mut last_error = io::Error::other("no address");
    for address in &server.addresses {
        match TcpStream::connect_timeout(&SocketAddr::new(*address, server.url.port), TIMEOUT) {
            Ok(socket) => {
                socket.set_read_timeout(Some(TIMEOUT))?;
                socket.set_write_timeout(Some(TIMEOUT))?;
                socket.set_nodelay(true)?;
                let tls = ClientConnection::new(Arc::clone(&server.tls), server.name.clone())
                    .map_err(io::Error::other)?;
                return Ok(BufReader::new(StreamOwned::new(tls, socket)));
            }
            Err(error) => last_error = error,
        }
    }
    Err(last_error)
}

fn socket_pair() -> io::Result<(OwnedFd, OwnedFd)> {
    let mut fds = [0; 2];
    // SAFETY: fills the two-element array on success, checked below.
    let result = unsafe {
        libc::socketpair(
            libc::AF_UNIX,
            libc::SOCK_SEQPACKET | libc::SOCK_CLOEXEC,
            0,
            fds.as_mut_ptr(),
        )
    };
    if result != 0 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: both descriptors were just created and belong to nobody else.
    Ok(unsafe { (OwnedFd::from_raw_fd(fds[0]), OwnedFd::from_raw_fd(fds[1])) })
}

/// Waits for the next message, empty ones included as the tunnel passes them on. None once the
/// tunnel closed its end, which an empty read alone cannot tell.
fn receive_query(socket: &OwnedFd, buffer: &mut [u8]) -> io::Result<Option<usize>> {
    let mut ready = libc::pollfd {
        fd: socket.as_raw_fd(),
        events: libc::POLLIN,
        revents: 0,
    };
    // SAFETY: polls one live descriptor.
    while unsafe { libc::poll(&raw mut ready, 1, -1) } < 0 {
        let error = io::Error::last_os_error();
        if error.kind() != io::ErrorKind::Interrupted {
            return Err(error);
        }
    }
    let length = receive(socket, buffer)?;
    Ok((length > 0 || ready.revents & libc::POLLHUP == 0).then_some(length))
}

fn receive(socket: &OwnedFd, buffer: &mut [u8]) -> io::Result<usize> {
    loop {
        // SAFETY: the buffer outlives the call, which writes within its length.
        let length = unsafe {
            libc::recv(
                socket.as_raw_fd(),
                buffer.as_mut_ptr().cast(),
                buffer.len(),
                0,
            )
        };
        if length >= 0 {
            return Ok(length as usize);
        }
        let error = io::Error::last_os_error();
        if error.kind() != io::ErrorKind::Interrupted {
            return Err(error);
        }
    }
}

fn send(socket: &OwnedFd, data: &[u8]) -> io::Result<usize> {
    // SAFETY: the data outlives the call, which reads within its length.
    let length = unsafe {
        libc::send(
            socket.as_raw_fd(),
            data.as_ptr().cast(),
            data.len(),
            libc::MSG_NOSIGNAL,
        )
    };
    if length < 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(length as usize)
    }
}

#[cfg(test)]
mod tests {
    use super::{receive, send, start_with, tls_config};
    use crate::url::DohUrl;
    use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
    use rustls::{ServerConfig, ServerConnection, StreamOwned};
    use std::io::{BufRead, BufReader, Read, Write};
    use std::net::{Ipv4Addr, TcpListener, TcpStream};
    use std::sync::Arc;
    use std::thread;

    /// A server answering each query with the query itself reversed, over kept-alive connections.
    fn serve_reversed(
        listener: TcpListener,
        certificate: CertificateDer<'static>,
        key: PrivateKeyDer<'static>,
    ) {
        let config = Arc::new(
            ServerConfig::builder_with_provider(Arc::new(rustls::crypto::ring::default_provider()))
                .with_safe_default_protocol_versions()
                .unwrap()
                .with_no_client_auth()
                .with_single_cert(vec![certificate], key)
                .unwrap(),
        );
        for socket in listener.incoming() {
            let connection = ServerConnection::new(Arc::clone(&config)).unwrap();
            thread::spawn(move || {
                answer_reversed(BufReader::new(StreamOwned::new(
                    connection,
                    socket.unwrap(),
                )))
            });
        }
    }

    fn answer_reversed(mut stream: BufReader<StreamOwned<ServerConnection, TcpStream>>) {
        loop {
            let mut length = 0;
            loop {
                let mut line = String::new();
                if stream.read_line(&mut line).unwrap_or(0) == 0 {
                    return;
                }
                if let Some(value) = line.strip_prefix("Content-Length: ") {
                    length = value.trim().parse().unwrap();
                }
                if line == "\r\n" {
                    break;
                }
            }
            let mut body = vec![0; length];
            stream.read_exact(&mut body).unwrap();
            body.reverse();
            let head = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\n\r\n", body.len());
            stream.get_mut().write_all(head.as_bytes()).unwrap();
            stream.get_mut().write_all(&body).unwrap();
            stream.get_mut().flush().unwrap();
        }
    }

    #[test]
    fn relays_each_query_to_the_server_and_back() {
        // A server certificate signed by an authority, as webpki wants it.
        let mut authority_params = rcgen::CertificateParams::new(Vec::new()).unwrap();
        authority_params.is_ca = rcgen::IsCa::Ca(rcgen::BasicConstraints::Unconstrained);
        let authority = rcgen::CertifiedIssuer::self_signed(
            authority_params,
            rcgen::KeyPair::generate().unwrap(),
        )
        .unwrap();
        let server_key = rcgen::KeyPair::generate().unwrap();
        let server_params = rcgen::CertificateParams::new(vec!["localhost".to_owned()]).unwrap();
        let certificate = server_params
            .signed_by(&server_key, &authority)
            .unwrap()
            .der()
            .clone();
        let key = PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(server_key.serialize_der()));
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        thread::spawn(move || serve_reversed(listener, certificate, key));

        let mut roots = rustls::RootCertStore::empty();
        roots.add(authority.der().clone()).unwrap();
        let url = DohUrl::parse(&format!("https://localhost:{port}/dns-query")).unwrap();
        let tunnel_end = start_with(
            url,
            vec![Ipv4Addr::LOCALHOST.into()],
            tls_config(roots.roots).unwrap(),
        )
        .unwrap();
        let mut buffer = [0u8; 512];
        // An empty message, as some browsers send, does not stop the relay.
        send(&tunnel_end, &[]).unwrap();
        for query in [&b"\x01\x02\x03"[..], b"\x04\x05"] {
            send(&tunnel_end, query).unwrap();
            let length = receive(&tunnel_end, &mut buffer).unwrap();
            let mut expected = query.to_vec();
            expected.reverse();
            assert_eq!(&buffer[..length], expected);
        }
    }
}
