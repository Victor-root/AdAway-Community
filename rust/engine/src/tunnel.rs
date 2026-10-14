//! The DNS filter loop of the VPN: reads the queries apps send to the virtual DNS servers, answers
//! blocked and redirected names itself, and relays the others to the real servers.

use std::collections::{HashMap, VecDeque};
use std::io;
use std::net::{IpAddr, SocketAddr};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};

use crate::dns::{self, parse_query};
use crate::filter::{BlockList, Verdict};
use crate::packet::{build_udp, parse_udp};
use crate::querylog::QueryLog;

const BUFFER_SIZE: usize = 65_536;
/// A relayed query not answered in time is forgotten: the app retries on its own.
const QUERY_TIMEOUT: Duration = Duration::from_secs(10);
const MAX_PENDING_QUERIES: usize = 1024;
const FIRST_PROBE_INTERVAL: Duration = Duration::from_secs(1);
const MAX_PROBE_INTERVAL: Duration = Duration::from_secs(4096);

/// The final list in use, replaced as a whole when the user applies a new configuration.
pub type SharedRules = Arc<RwLock<Arc<BlockList>>>;

/// A real DNS server, behind the virtual address apps send their queries to.
pub struct Upstream {
    pub virtual_address: IpAddr,
    /// UDP socket connected to the real server, and kept out of the VPN by the app.
    pub socket: OwnedFd,
}

pub struct TunnelConfig {
    pub tun: OwnedFd,
    pub upstreams: Vec<Upstream>,
    /// Whether to probe the network when the tunnel stays silent, to notice a lost network.
    pub monitor: bool,
    pub log: Arc<QueryLog>,
}

/// Why [`Tunnel::run`] returned.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Exit {
    Stopped,
    TunnelClosed,
    NetworkUnreachable,
}

/// Wakes up a running tunnel to stop it, from any thread.
#[derive(Clone)]
pub struct Stopper(Arc<OwnedFd>);

impl Stopper {
    pub fn stop(&self) {
        let value: u64 = 1;
        // SAFETY: writes 8 bytes from a valid u64 to an eventfd the stopper owns.
        unsafe { libc::write(self.0.as_raw_fd(), (&raw const value).cast(), 8) };
    }
}

struct PendingQuery {
    client: SocketAddr,
    virtual_server: SocketAddr,
    original_id: u16,
    deadline: Instant,
}

/// Relayed queries waiting for an answer, under identifiers chosen per upstream server so that
/// two apps using the same identifier never get each other's answer.
#[derive(Default)]
struct Pending {
    queries: HashMap<(usize, u16), PendingQuery>,
    order: VecDeque<(usize, u16, Instant)>,
    next_ids: HashMap<usize, u16>,
}

impl Pending {
    fn insert(&mut self, upstream: usize, query: PendingQuery) -> u16 {
        while self.queries.len() >= MAX_PENDING_QUERIES {
            let Some((index, id, deadline)) = self.order.pop_front() else {
                break;
            };
            self.remove_if_current(index, id, deadline);
        }
        let next = self.next_ids.entry(upstream).or_default();
        while self.queries.contains_key(&(upstream, *next)) {
            *next = next.wrapping_add(1);
        }
        let id = *next;
        *next = next.wrapping_add(1);
        self.order.push_back((upstream, id, query.deadline));
        self.queries.insert((upstream, id), query);
        id
    }

    fn take(&mut self, upstream: usize, id: u16) -> Option<PendingQuery> {
        self.queries.remove(&(upstream, id))
    }

    fn expire(&mut self, now: Instant) {
        while let Some(&(index, id, deadline)) = self.order.front() {
            if deadline > now {
                break;
            }
            self.order.pop_front();
            self.remove_if_current(index, id, deadline);
        }
    }

    /// Removes the entry unless the identifier was answered and reused since.
    fn remove_if_current(&mut self, upstream: usize, id: u16, deadline: Instant) {
        if self
            .queries
            .get(&(upstream, id))
            .is_some_and(|query| query.deadline == deadline)
        {
            self.queries.remove(&(upstream, id));
        }
    }

    fn next_deadline(&self) -> Option<Instant> {
        self.order.front().map(|&(_, _, deadline)| deadline)
    }
}

pub struct Tunnel {
    config: TunnelConfig,
    rules: SharedRules,
    wake: Arc<OwnedFd>,
    pending: Pending,
    last_activity: Instant,
    probe_interval: Duration,
}

impl Tunnel {
    pub fn new(config: TunnelConfig, rules: SharedRules) -> io::Result<Self> {
        // SAFETY: plain system call, its result is checked before use.
        let wake = unsafe { libc::eventfd(0, libc::EFD_CLOEXEC | libc::EFD_NONBLOCK) };
        if wake < 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(Self {
            config,
            rules,
            // SAFETY: the descriptor was just created and is owned by nobody else.
            wake: Arc::new(unsafe { OwnedFd::from_raw_fd(wake) }),
            pending: Pending::default(),
            last_activity: Instant::now(),
            probe_interval: FIRST_PROBE_INTERVAL,
        })
    }

    pub fn stopper(&self) -> Stopper {
        Stopper(Arc::clone(&self.wake))
    }

    /// Filters until stopped, or until the tunnel or the network fails.
    pub fn run(&mut self) -> Exit {
        let mut buffer = vec![0u8; BUFFER_SIZE];
        let mut descriptors: Vec<libc::pollfd> =
            [self.wake.as_raw_fd(), self.config.tun.as_raw_fd()]
                .into_iter()
                .chain(
                    self.config
                        .upstreams
                        .iter()
                        .map(|upstream| upstream.socket.as_raw_fd()),
                )
                .map(|fd| libc::pollfd {
                    fd,
                    events: libc::POLLIN,
                    revents: 0,
                })
                .collect();
        loop {
            let timeout = self.poll_timeout();
            // SAFETY: the pointer and length describe the live `descriptors` vector.
            let ready = unsafe {
                libc::poll(
                    descriptors.as_mut_ptr(),
                    descriptors.len() as libc::nfds_t,
                    timeout,
                )
            };
            if ready < 0 {
                if io::Error::last_os_error().kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                return Exit::TunnelClosed;
            }
            if descriptors[0].revents != 0 {
                return Exit::Stopped;
            }
            let tun_events = descriptors[1].revents;
            if tun_events & (libc::POLLERR | libc::POLLHUP | libc::POLLNVAL) != 0 {
                return Exit::TunnelClosed;
            }
            if tun_events & libc::POLLIN != 0 {
                match receive(self.config.tun.as_raw_fd(), &mut buffer) {
                    Ok(length) if length > 0 => {
                        if let Some(exit) = self.handle_query(&buffer[..length]) {
                            return exit;
                        }
                    }
                    // A tun device never yields an empty packet: an empty read means it was closed.
                    Ok(_) => return Exit::TunnelClosed,
                    Err(error) if is_transient(&error) => {}
                    Err(_) => return Exit::TunnelClosed,
                }
            }
            for index in 0..self.config.upstreams.len() {
                if descriptors[index + 2].revents & libc::POLLIN != 0 {
                    let socket = self.config.upstreams[index].socket.as_raw_fd();
                    if let Ok(length) = receive(socket, &mut buffer) {
                        self.handle_answer(index, &buffer[..length]);
                    }
                }
            }
            let now = Instant::now();
            self.pending.expire(now);
            if let Some(exit) = self.probe_if_silent(now) {
                return exit;
            }
        }
    }

    fn poll_timeout(&self) -> libc::c_int {
        let now = Instant::now();
        let probe = self
            .config
            .monitor
            .then(|| self.last_activity + self.probe_interval);
        let next = [self.pending.next_deadline(), probe]
            .into_iter()
            .flatten()
            .min();
        match next {
            None => -1,
            Some(deadline) => deadline
                .saturating_duration_since(now)
                .as_millis()
                .min(i32::MAX as u128) as libc::c_int,
        }
    }

    fn handle_query(&mut self, packet: &[u8]) -> Option<Exit> {
        let packet = parse_udp(packet)?;
        let index = self
            .config
            .upstreams
            .iter()
            .position(|upstream| upstream.virtual_address == packet.destination.ip())?;
        self.last_activity = Instant::now();
        let socket = self.config.upstreams[index].socket.as_raw_fd();
        if packet.payload.is_empty() {
            // Some browsers warm a connection up with an empty datagram: pass it on, nothing comes back.
            let _ = send(socket, &[]);
            return None;
        }
        let query = parse_query(packet.payload)?;
        self.config.log.record(&query.name);
        let rules = self.rules.read().map(|rules| Arc::clone(&rules)).ok()?;
        let response = match rules.verdict(&query.name) {
            Verdict::Block => query.blocked_response(),
            Verdict::Redirect(address) => query.redirected_response(address),
            Verdict::Allow => {
                let pending = PendingQuery {
                    client: packet.source,
                    virtual_server: packet.destination,
                    original_id: query.id,
                    deadline: Instant::now() + QUERY_TIMEOUT,
                };
                let id = self.pending.insert(index, pending);
                let mut message = packet.payload.to_vec();
                dns::set_message_id(&mut message, id);
                return match send(socket, &message) {
                    Ok(_) => None,
                    Err(error) => {
                        self.pending.take(index, id);
                        is_network_unreachable(&error).then_some(Exit::NetworkUnreachable)
                    }
                };
            }
        };
        self.write_to_tunnel(packet.destination, packet.source, &response)
    }

    fn handle_answer(&mut self, upstream: usize, answer: &[u8]) {
        let Some(query) = dns::message_id(answer).and_then(|id| self.pending.take(upstream, id))
        else {
            return;
        };
        let mut message = answer.to_vec();
        dns::set_message_id(&mut message, query.original_id);
        let _ = self.write_to_tunnel(query.virtual_server, query.client, &message);
    }

    fn write_to_tunnel(
        &self,
        source: SocketAddr,
        destination: SocketAddr,
        payload: &[u8],
    ) -> Option<Exit> {
        let packet = build_udp(source, destination, payload)?;
        match send(self.config.tun.as_raw_fd(), &packet) {
            Err(error)
                if error.raw_os_error() == Some(libc::EBADF)
                    || error.raw_os_error() == Some(libc::EIO) =>
            {
                Some(Exit::TunnelClosed)
            }
            _ => None,
        }
    }

    /// A silent tunnel is not a dead one: probes only space out, and only a failure to send matters.
    fn probe_if_silent(&mut self, now: Instant) -> Option<Exit> {
        if !self.config.monitor || now < self.last_activity + self.probe_interval {
            return None;
        }
        self.last_activity = now;
        self.probe_interval = (self.probe_interval * 4).min(MAX_PROBE_INTERVAL);
        let upstream = self.config.upstreams.last()?;
        match send(upstream.socket.as_raw_fd(), &[]) {
            Err(error) if is_network_unreachable(&error) => Some(Exit::NetworkUnreachable),
            _ => None,
        }
    }
}

fn receive(fd: RawFd, buffer: &mut [u8]) -> io::Result<usize> {
    // SAFETY: reads at most `buffer.len()` bytes into the live buffer.
    let length = unsafe { libc::read(fd, buffer.as_mut_ptr().cast(), buffer.len()) };
    if length < 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(length as usize)
    }
}

fn send(fd: RawFd, data: &[u8]) -> io::Result<usize> {
    // SAFETY: writes `data.len()` bytes from the live slice.
    let length = unsafe { libc::write(fd, data.as_ptr().cast(), data.len()) };
    if length < 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(length as usize)
    }
}

fn is_transient(error: &io::Error) -> bool {
    matches!(
        error.kind(),
        io::ErrorKind::Interrupted | io::ErrorKind::WouldBlock
    )
}

/// The network the upstream servers are reached through is gone or refuses the traffic.
fn is_network_unreachable(error: &io::Error) -> bool {
    matches!(
        error.raw_os_error(),
        Some(libc::ENETUNREACH | libc::EHOSTUNREACH | libc::ENETDOWN | libc::EPERM | libc::EACCES)
    )
}

#[cfg(test)]
mod tests {
    use super::{Exit, SharedRules, Tunnel, TunnelConfig, Upstream};
    use crate::dns::tests::query;
    use crate::filter::BlockList;
    use crate::packet::{build_udp, parse_udp};
    use crate::querylog::QueryLog;
    use crate::rule;
    use std::net::{SocketAddr, UdpSocket};
    use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::{Arc, RwLock};
    use std::thread;
    use std::time::Duration;

    const CLIENT: &str = "10.0.0.2:41000";
    const VIRTUAL_SERVER: &str = "192.0.2.2:53";

    struct Harness {
        app: OwnedFd,
        server: UdpSocket,
        runner: thread::JoinHandle<Exit>,
        stopper: super::Stopper,
        log: Arc<QueryLog>,
    }

    fn start(blocked: &[&str]) -> Harness {
        static NEXT: AtomicUsize = AtomicUsize::new(0);
        let unique = NEXT.fetch_add(1, Ordering::Relaxed);
        let directory =
            std::env::temp_dir().join(format!("adaway-tunnel-{}-{unique}", std::process::id()));
        std::fs::create_dir_all(&directory).unwrap();
        rule::write_lines(&directory.join("blocked.txt"), blocked).unwrap();
        let rules: SharedRules =
            Arc::new(RwLock::new(Arc::new(BlockList::load(&directory).unwrap())));
        std::fs::remove_dir_all(&directory).unwrap();

        let (app, tun) = packet_pair();
        let server = UdpSocket::bind("127.0.0.1:0").unwrap();
        let upstream = UdpSocket::bind("127.0.0.1:0").unwrap();
        upstream.connect(server.local_addr().unwrap()).unwrap();
        let log = Arc::new(QueryLog::default());
        log.set_recording(true);
        let config = TunnelConfig {
            tun,
            upstreams: vec![Upstream {
                virtual_address: VIRTUAL_SERVER.parse::<SocketAddr>().unwrap().ip(),
                socket: OwnedFd::from(upstream),
            }],
            monitor: false,
            log: Arc::clone(&log),
        };
        let mut tunnel = Tunnel::new(config, rules).unwrap();
        let stopper = tunnel.stopper();
        let runner = thread::spawn(move || tunnel.run());
        server
            .set_read_timeout(Some(Duration::from_secs(5)))
            .unwrap();
        Harness {
            app,
            server,
            runner,
            stopper,
            log,
        }
    }

    /// A packet socket pair behaves like a tun device: one packet per read, and an end of file
    /// once the other side is closed.
    fn packet_pair() -> (OwnedFd, OwnedFd) {
        let mut fds = [0; 2];
        // SAFETY: fills the two-element array on success, checked below.
        assert_eq!(
            unsafe { libc::socketpair(libc::AF_UNIX, libc::SOCK_SEQPACKET, 0, fds.as_mut_ptr()) },
            0
        );
        // SAFETY: both descriptors were just created and belong to nobody else.
        unsafe { (OwnedFd::from_raw_fd(fds[0]), OwnedFd::from_raw_fd(fds[1])) }
    }

    fn send_query(harness: &Harness, name: &str) {
        let packet = build_udp(
            CLIENT.parse().unwrap(),
            VIRTUAL_SERVER.parse().unwrap(),
            &query(name, 1),
        )
        .unwrap();
        super::send(harness.app.as_raw_fd(), &packet).unwrap();
    }

    fn receive_answer(harness: &Harness) -> Vec<u8> {
        let mut ready = libc::pollfd {
            fd: harness.app.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        };
        // SAFETY: polls one live descriptor.
        assert_eq!(
            unsafe { libc::poll(&raw mut ready, 1, 5000) },
            1,
            "an answer within 5 seconds"
        );
        let mut buffer = [0u8; 4096];
        let length = super::receive(harness.app.as_raw_fd(), &mut buffer).unwrap();
        let packet = parse_udp(&buffer[..length]).unwrap();
        assert_eq!(packet.source, VIRTUAL_SERVER.parse().unwrap());
        assert_eq!(packet.destination, CLIENT.parse().unwrap());
        packet.payload.to_vec()
    }

    /// Reads and drops whatever the tunnel answered so far.
    fn drain(harness: &Harness) {
        let mut buffer = [0u8; 4096];
        loop {
            let mut ready = libc::pollfd {
                fd: harness.app.as_raw_fd(),
                events: libc::POLLIN,
                revents: 0,
            };
            // SAFETY: polls one live descriptor.
            if unsafe { libc::poll(&raw mut ready, 1, 20) } != 1 {
                return;
            }
            let _ = super::receive(harness.app.as_raw_fd(), &mut buffer);
        }
    }

    fn stop(harness: Harness) {
        harness.stopper.stop();
        assert_eq!(harness.runner.join().unwrap(), Exit::Stopped);
    }

    #[test]
    fn answers_blocked_names_locally() {
        let harness = start(&["ads.example.com"]);
        send_query(&harness, "ads.example.com");
        let answer = receive_answer(&harness);
        assert_eq!(&answer[..4], &[0x12, 0x34, 0x81, 0x80]);
        assert_eq!(
            &answer[8..10],
            &[0, 1],
            "an SOA record in the authority section"
        );
        assert_eq!(harness.log.names(), ["ads.example.com"]);
        stop(harness);
    }

    #[test]
    fn relays_other_names_to_the_server() {
        let harness = start(&[]);
        send_query(&harness, "example.com");
        let mut buffer = [0u8; 4096];
        let (length, relay) = harness.server.recv_from(&mut buffer).unwrap();
        let relayed = buffer[..length].to_vec();
        assert_eq!(
            &relayed[2..],
            &query("example.com", 1)[2..],
            "same query, own identifier"
        );
        let mut reply = relayed.clone();
        reply[2] |= 0x80;
        harness.server.send_to(&reply, relay).unwrap();
        let answer = receive_answer(&harness);
        assert_eq!(&answer[..2], &[0x12, 0x34], "original identifier restored");
        assert_eq!(&answer[2..], &reply[2..]);
        stop(harness);
    }

    #[test]
    fn survives_garbage_and_reports_a_closed_tunnel() {
        let harness = start(&["ads.example.com"]);
        for garbage in [&[0x45, 0, 0][..], &[0x60; 60], &[0xff; 200]] {
            super::send(harness.app.as_raw_fd(), garbage).unwrap();
        }
        let valid = build_udp(
            CLIENT.parse().unwrap(),
            VIRTUAL_SERVER.parse().unwrap(),
            &query("ads.example.com", 1),
        )
        .unwrap();
        let mut state = 0x2545_f491_4f6c_dd1du64;
        for round in 0..3000 {
            if round % 50 == 0 {
                drain(&harness);
            }
            let mut packet = valid.clone();
            for _ in 0..3 {
                state ^= state << 13;
                state ^= state >> 7;
                state ^= state << 17;
                let index = (state as usize) % packet.len();
                packet[index] = (state >> 40) as u8;
            }
            super::send(harness.app.as_raw_fd(), &packet).unwrap();
        }
        drain(&harness);
        send_query(&harness, "ads.example.com");
        assert!(
            !receive_answer(&harness).is_empty(),
            "still filtering after garbage"
        );
        drop(harness.app);
        assert_eq!(harness.runner.join().unwrap(), Exit::TunnelClosed);
    }

    #[test]
    fn stops_on_request() {
        let harness = start(&[]);
        stop(harness);
    }

    #[test]
    fn keeps_stoppers_valid_after_the_tunnel() {
        let harness = start(&[]);
        let stopper = harness.stopper.clone();
        stop(harness);
        stopper.stop();
    }
}
