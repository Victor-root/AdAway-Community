//! The listening sockets, and the connections, each answered on its own thread.

use std::io::{self, Write};
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr, TcpListener, TcpStream};
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::thread;
use std::time::Duration;

use rustls::{ServerConfig, ServerConnection, StreamOwned};

use crate::http::{self, Content};

const HTTP_PORT: u16 = 80;
const HTTPS_PORT: u16 = 443;
/// Past this, new connections are closed at once rather than piling up threads.
const MAX_CONNECTIONS: usize = 64;
const TIMEOUT: Duration = Duration::from_secs(10);
/// How long a previous server still stopping may hold the ports.
const BIND_RETRIES: u32 = 30;
const BIND_RETRY_DELAY: Duration = Duration::from_millis(100);

pub struct Listener {
    socket: TcpListener,
    tls: bool,
}

/// Binds the HTTP and HTTPS ports on the IPv4 and IPv6 loopback addresses. The IPv6 one is
/// skipped on a device without it.
pub fn bind() -> io::Result<Vec<Listener>> {
    let mut listeners = Vec::new();
    for address in [
        IpAddr::V4(Ipv4Addr::LOCALHOST),
        IpAddr::V6(Ipv6Addr::LOCALHOST),
    ] {
        for (port, tls) in [(HTTP_PORT, false), (HTTPS_PORT, true)] {
            if let Some(socket) = bind_one(SocketAddr::new(address, port))? {
                listeners.push(Listener { socket, tls });
            }
        }
    }
    Ok(listeners)
}

fn bind_one(address: SocketAddr) -> io::Result<Option<TcpListener>> {
    let mut retries = 0;
    loop {
        match TcpListener::bind(address) {
            Ok(socket) => return Ok(Some(socket)),
            Err(error) if address.is_ipv6() && is_ipv6_missing(&error) => return Ok(None),
            Err(error) if error.kind() == io::ErrorKind::AddrInUse && retries < BIND_RETRIES => {
                retries += 1;
                thread::sleep(BIND_RETRY_DELAY);
            }
            Err(error) => return Err(error),
        }
    }
}

fn is_ipv6_missing(error: &io::Error) -> bool {
    error.kind() == io::ErrorKind::AddrNotAvailable
        || error.raw_os_error() == Some(libc::EAFNOSUPPORT)
}

/// Answers the connections. Returns only if every listener failed.
pub fn serve(listeners: Vec<Listener>, content: Arc<Content>, tls: Arc<ServerConfig>) {
    let active = Arc::new(AtomicUsize::new(0));
    let threads: Vec<_> = listeners
        .into_iter()
        .map(|listener| {
            let (content, tls, active) =
                (Arc::clone(&content), Arc::clone(&tls), Arc::clone(&active));
            thread::spawn(move || accept(listener, &content, &tls, &active))
        })
        .collect();
    for thread in threads {
        let _ = thread.join();
    }
}

fn accept(
    listener: Listener,
    content: &Arc<Content>,
    tls: &Arc<ServerConfig>,
    active: &Arc<AtomicUsize>,
) {
    for stream in listener.socket.incoming() {
        let Ok(stream) = stream else {
            // Out of file descriptors for instance: give the open connections time to end.
            thread::sleep(BIND_RETRY_DELAY);
            continue;
        };
        let Some(slot) = Slot::take(active) else {
            continue;
        };
        let content = Arc::clone(content);
        let tls = listener.tls.then(|| Arc::clone(tls));
        thread::spawn(move || {
            let _slot = slot;
            let _ = handle(stream, &content, tls);
        });
    }
}

fn handle(stream: TcpStream, content: &Content, tls: Option<Arc<ServerConfig>>) -> io::Result<()> {
    stream.set_read_timeout(Some(TIMEOUT))?;
    stream.set_write_timeout(Some(TIMEOUT))?;
    match tls {
        None => http::answer(&mut &stream, content),
        Some(config) => {
            let connection = ServerConnection::new(config).map_err(io::Error::other)?;
            let mut stream = StreamOwned::new(connection, stream);
            http::answer(&mut stream, content)?;
            stream.conn.send_close_notify();
            stream.flush()
        }
    }
}

/// A connection counted as active until dropped.
struct Slot(Arc<AtomicUsize>);

impl Slot {
    fn take(active: &Arc<AtomicUsize>) -> Option<Self> {
        if active.fetch_add(1, Ordering::Relaxed) >= MAX_CONNECTIONS {
            active.fetch_sub(1, Ordering::Relaxed);
            return None;
        }
        Some(Slot(Arc::clone(active)))
    }
}

impl Drop for Slot {
    fn drop(&mut self) {
        self.0.fetch_sub(1, Ordering::Relaxed);
    }
}
