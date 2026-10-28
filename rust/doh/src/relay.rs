//! The relay between the VPN tunnel and a DNS over HTTPS server: to the tunnel, a datagram socket
//! like the ones connected to the plain DNS servers; on the other side, the queries go to the
//! server over one HTTP/2 connection, or over a few kept-alive HTTP/1.1 connections with the
//! servers that only speak it. It stops once the tunnel closes its end.

use std::future::Future;
use std::io;
use std::net::{IpAddr, SocketAddr};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::Duration;

use bytes::Bytes;
use h2::client::SendRequest;
use rustls::pki_types::{ServerName, TrustAnchor};
use rustls::{ClientConfig, RootCertStore};
use tokio::io::BufReader;
use tokio::io::unix::AsyncFd;
use tokio::net::TcpStream;
use tokio::sync::Semaphore;
use tokio_rustls::TlsConnector;
use tokio_rustls::client::TlsStream;

use crate::http;
use crate::url::DohUrl;

/// How many queries can be on their way at once over HTTP/1.1.
const CONNECTIONS: usize = 4;
const TIMEOUT: Duration = Duration::from_secs(5);
const BUFFER_SIZE: usize = 65_536;
const HTTP2: &[u8] = b"h2";
const HTTP1: &[u8] = b"http/1.1";

type Http1Connection = Box<BufReader<TlsStream<TcpStream>>>;

/// A new connection, in the version of HTTP the server chose.
enum Connection {
    Http2(SendRequest<Bytes>),
    Http1(Http1Connection),
}

/// What every connection to the server needs, and the connections kept for the next queries.
struct Server {
    url: DohUrl,
    name: ServerName<'static>,
    addresses: Vec<IpAddr>,
    tls: TlsConnector,
    http2: Mutex<Option<SendRequest<Bytes>>>,
    idle_http1: Mutex<Vec<Http1Connection>>,
    http1_slots: Semaphore,
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
        tls: TlsConnector::from(Arc::new(tls)),
        http2: Mutex::new(None),
        idle_http1: Mutex::new(Vec::new()),
        http1_slots: Semaphore::new(CONNECTIONS),
    });
    let (tunnel_end, relay_end) = socket_pair()?;
    set_nonblocking(&relay_end)?;
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_io()
        .enable_time()
        .build()?;
    // Dropping the runtime once the tunnel closed its end also ends the connections.
    thread::spawn(move || runtime.block_on(relay(server, relay_end)));
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
    config.alpn_protocols = vec![HTTP2.to_vec(), HTTP1.to_vec()];
    Ok(config)
}

/// Answers each query of the tunnel on its own, until the tunnel closes its end.
async fn relay(server: Arc<Server>, relay_end: OwnedFd) {
    // SAFETY: the socket, owned by the AsyncFd, stays open and the same as long as it lives.
    let Ok(socket) = (unsafe { AsyncFd::register(relay_end) }) else {
        return;
    };
    let socket = Arc::new(socket);
    let mut buffer = vec![0u8; BUFFER_SIZE];
    while let Ok(Some(length)) = receive_query(&socket, &mut buffer).await {
        if length == 0 {
            continue;
        }
        let query = buffer[..length].to_vec();
        let (server, socket) = (Arc::clone(&server), Arc::clone(&socket));
        tokio::spawn(async move {
            // A kept-alive connection the server closed meanwhile fails at once: one more try.
            let answer = match server.exchange(&query).await {
                Ok(answer) => Ok(answer),
                Err(_) => server.exchange(&query).await,
            };
            // An unanswered query is retried by the app itself.
            if let Ok(answer) = answer {
                let _ = send_answer(&socket, &answer).await;
            }
        });
    }
}

/// The outcome of `step`, or a time out error after [`TIMEOUT`].
async fn within_timeout<T>(step: impl Future<Output = io::Result<T>>) -> io::Result<T> {
    tokio::time::timeout(TIMEOUT, step)
        .await
        .unwrap_or_else(|_| Err(io::ErrorKind::TimedOut.into()))
}

impl Server {
    async fn exchange(&self, query: &[u8]) -> io::Result<Vec<u8>> {
        let shared = lock(&self.http2).clone();
        if let Some(sender) = shared {
            return self.exchange_http2(sender, query).await;
        }
        let _slot = self.http1_slots.acquire().await.map_err(io::Error::other)?;
        let idle = lock(&self.idle_http1).pop();
        let mut connection = match idle {
            Some(connection) => connection,
            None => match self.connect().await? {
                Connection::Http2(sender) => {
                    *lock(&self.http2) = Some(sender.clone());
                    return self.exchange_http2(sender, query).await;
                }
                Connection::Http1(connection) => connection,
            },
        };
        let answer = within_timeout(async {
            http::write_query(connection.get_mut(), &self.url, query).await?;
            http::read_answer(&mut connection).await
        })
        .await?;
        if answer.keep_alive {
            lock(&self.idle_http1).push(connection);
        }
        Ok(answer.message)
    }

    /// Forgets the HTTP/2 connection when it fails, for the next query to make a new one.
    async fn exchange_http2(
        &self,
        sender: SendRequest<Bytes>,
        query: &[u8],
    ) -> io::Result<Vec<u8>> {
        let result = within_timeout(http::exchange_http2(sender, &self.url, query)).await;
        if result.is_err() {
            *lock(&self.http2) = None;
        }
        result
    }

    async fn connect(&self) -> io::Result<Connection> {
        let socket = self.connect_socket().await?;
        socket.set_nodelay(true)?;
        within_timeout(async {
            let stream = self.tls.connect(self.name.clone(), socket).await?;
            if stream.get_ref().1.alpn_protocol() == Some(HTTP2) {
                let (sender, connection) = h2::client::handshake(stream)
                    .await
                    .map_err(io::Error::other)?;
                tokio::spawn(connection);
                Ok(Connection::Http2(sender))
            } else {
                Ok(Connection::Http1(Box::new(BufReader::new(stream))))
            }
        })
        .await
    }

    async fn connect_socket(&self) -> io::Result<TcpStream> {
        let mut last_error = io::Error::other("no address");
        for address in &self.addresses {
            match within_timeout(TcpStream::connect(SocketAddr::new(*address, self.url.port))).await
            {
                Ok(socket) => return Ok(socket),
                Err(error) => last_error = error,
            }
        }
        Err(last_error)
    }
}

/// Nothing panics while holding these locks, so a poisoned one still holds sound data.
fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
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

fn set_nonblocking(socket: &OwnedFd) -> io::Result<()> {
    // SAFETY: reads then sets the flags of one live descriptor.
    let result = unsafe {
        let flags = libc::fcntl(socket.as_raw_fd(), libc::F_GETFL);
        if flags < 0 {
            flags
        } else {
            libc::fcntl(socket.as_raw_fd(), libc::F_SETFL, flags | libc::O_NONBLOCK)
        }
    };
    if result < 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(())
    }
}

/// Waits for the next message, empty ones included as the tunnel passes them on. None once the
/// tunnel closed its end, which an empty read alone cannot tell.
async fn receive_query(socket: &AsyncFd<OwnedFd>, buffer: &mut [u8]) -> io::Result<Option<usize>> {
    loop {
        let mut ready = socket.readable().await?;
        let closed = ready.ready().is_read_closed();
        if let Ok(length) = ready.try_io(|socket| receive(socket.get_ref(), buffer)) {
            let length = length?;
            return Ok((length > 0 || !closed).then_some(length));
        }
    }
}

async fn send_answer(socket: &AsyncFd<OwnedFd>, answer: &[u8]) -> io::Result<usize> {
    loop {
        let mut ready = socket.writable().await?;
        if let Ok(result) = ready.try_io(|socket| send(socket.get_ref(), answer)) {
            return result;
        }
    }
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
    use super::{HTTP1, HTTP2, receive, send, start_with, tls_config};
    use crate::url::DohUrl;
    use rustls::ServerConfig;
    use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
    use std::net::{Ipv4Addr, TcpListener};
    use std::sync::Arc;
    use std::thread;
    use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
    use tokio_rustls::TlsAcceptor;

    /// A server answering each query with the query itself reversed, over kept-alive connections,
    /// in the versions of HTTP of `protocols`.
    fn serve_reversed(
        listener: TcpListener,
        certificate: CertificateDer<'static>,
        key: PrivateKeyDer<'static>,
        protocols: &[&[u8]],
    ) {
        let mut config =
            ServerConfig::builder_with_provider(Arc::new(rustls::crypto::ring::default_provider()))
                .with_safe_default_protocol_versions()
                .unwrap()
                .with_no_client_auth()
                .with_single_cert(vec![certificate], key)
                .unwrap();
        config.alpn_protocols = protocols.iter().map(|protocol| protocol.to_vec()).collect();
        let acceptor = TlsAcceptor::from(Arc::new(config));
        listener.set_nonblocking(true).unwrap();
        tokio::runtime::Builder::new_current_thread()
            .enable_io()
            .build()
            .unwrap()
            .block_on(async move {
                let listener = tokio::net::TcpListener::from_std(listener).unwrap();
                loop {
                    let (socket, _) = listener.accept().await.unwrap();
                    let acceptor = acceptor.clone();
                    tokio::spawn(async move {
                        let stream = acceptor.accept(socket).await.unwrap();
                        if stream.get_ref().1.alpn_protocol() == Some(HTTP2) {
                            answer_reversed_http2(stream).await;
                        } else {
                            answer_reversed_http1(BufReader::new(stream)).await;
                        }
                    });
                }
            });
    }

    async fn answer_reversed_http2(stream: tokio_rustls::server::TlsStream<tokio::net::TcpStream>) {
        let mut connection = h2::server::handshake(stream).await.unwrap();
        while let Some(Ok((request, mut respond))) = connection.accept().await {
            tokio::spawn(async move {
                let length = request.headers()[http::header::CONTENT_LENGTH].clone();
                let mut body = request.into_body();
                let mut query = Vec::new();
                while let Some(data) = body.data().await {
                    query.extend_from_slice(&data.unwrap());
                }
                assert_eq!(length, query.len().to_string());
                query.reverse();
                let response = http::Response::builder().status(200).body(()).unwrap();
                let mut stream = respond.send_response(response, false).unwrap();
                stream.send_data(query.into(), true).unwrap();
            });
        }
    }

    async fn answer_reversed_http1(
        mut stream: BufReader<tokio_rustls::server::TlsStream<tokio::net::TcpStream>>,
    ) {
        loop {
            let mut length = 0;
            loop {
                let mut line = String::new();
                if stream.read_line(&mut line).await.unwrap_or(0) == 0 {
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
            stream.read_exact(&mut body).await.unwrap();
            body.reverse();
            let head = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\n\r\n", body.len());
            stream.get_mut().write_all(head.as_bytes()).await.unwrap();
            stream.get_mut().write_all(&body).await.unwrap();
            stream.get_mut().flush().await.unwrap();
        }
    }

    /// Relays a few queries to a server speaking the versions of HTTP of `protocols`.
    fn relays_each_query_to_a_server_speaking(protocols: &'static [&'static [u8]]) {
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
        thread::spawn(move || serve_reversed(listener, certificate, key, protocols));

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

    #[test]
    fn relays_each_query_over_http2() {
        relays_each_query_to_a_server_speaking(&[HTTP2, HTTP1]);
    }

    #[test]
    fn relays_each_query_over_http1_with_the_servers_that_only_speak_it() {
        relays_each_query_to_a_server_speaking(&[HTTP1]);
    }

    #[test]
    fn relays_each_query_over_http1_without_protocol_negotiation() {
        relays_each_query_to_a_server_speaking(&[]);
    }
}
