//! Started as root by the app while the user records the DNS log in root mode: notes the names
//! the device asks its DNS servers for, each once, at the end of the file the app reads. The names
//! the hosts file blocks are answered on the device and never seen.
//!
//! Usage: `adaway-capture --output <file>`.

use std::collections::HashSet;
use std::fs::{File, OpenOptions};
use std::io::{self, Write};
use std::mem;
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::process::ExitCode;

use adaway_engine::dns::parse_query;
use adaway_engine::hostname;
use adaway_engine::packet::parse_udp;
use adaway_root::daemon;

/// Exit codes, mirrored in the app.
const EXIT_USAGE: u8 = 1;
const EXIT_SOCKET: u8 = 2;
const EXIT_OUTPUT: u8 = 3;
const EXIT_START: u8 = 4;

const DNS_PORT: u16 = 53;
/// A packet leaving the device, from `<linux/if_packet.h>`.
const PACKET_OUTGOING: u8 = 4;
/// Past this, names already written may be written again; the app keeps each once anyway.
const MAX_KNOWN_NAMES: usize = 8192;

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err((code, message)) => {
            eprintln!("{message}");
            ExitCode::from(code)
        }
    }
}

fn run() -> Result<(), (u8, String)> {
    let mut arguments = std::env::args().skip(1);
    let output = match (
        arguments.next().as_deref(),
        arguments.next(),
        arguments.next(),
    ) {
        (Some("--output"), Some(output), None) => output,
        _ => return Err((EXIT_USAGE, "usage: --output <file>".to_owned())),
    };
    let socket = open_socket()
        .map_err(|error| (EXIT_SOCKET, format!("capture socket not opened: {error}")))?;
    let file = OpenOptions::new()
        .append(true)
        .create(true)
        .open(&output)
        .map_err(|error| (EXIT_OUTPUT, format!("{output} not opened: {error}")))?;
    daemon::detach().map_err(|error| (EXIT_START, format!("not detached: {error}")))?;
    let error = capture(&socket, file);
    Err((EXIT_START, format!("capture ended: {error}")))
}

/// A socket receiving every network packet of the device, from its IP header on.
fn open_socket() -> io::Result<OwnedFd> {
    let protocol = i32::from((libc::ETH_P_ALL as u16).to_be());
    // SAFETY: a plain system call, its result checked below.
    let fd = unsafe {
        libc::socket(
            libc::AF_PACKET,
            libc::SOCK_DGRAM | libc::SOCK_CLOEXEC,
            protocol,
        )
    };
    if fd < 0 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: the descriptor was just created and belongs to nobody else.
    Ok(unsafe { OwnedFd::from_raw_fd(fd) })
}

/// Returns only on a failure.
fn capture(socket: &OwnedFd, mut output: File) -> io::Error {
    let mut known = HashSet::new();
    let mut buffer = [0u8; 65_536];
    loop {
        // SAFETY: an all-zero sockaddr_ll is a valid value of this plain C structure.
        let mut address: libc::sockaddr_ll = unsafe { mem::zeroed() };
        let mut address_length = mem::size_of::<libc::sockaddr_ll>() as libc::socklen_t;
        // SAFETY: the buffer and the address outlive the call, which writes within their sizes.
        let length = unsafe {
            libc::recvfrom(
                socket.as_raw_fd(),
                buffer.as_mut_ptr().cast(),
                buffer.len(),
                0,
                (&raw mut address).cast(),
                &raw mut address_length,
            )
        };
        if length < 0 {
            let error = io::Error::last_os_error();
            if error.kind() == io::ErrorKind::Interrupted {
                continue;
            }
            return error;
        }
        // Each query is seen once, leaving the device.
        if address.sll_pkttype != PACKET_OUTGOING {
            continue;
        }
        let Some(name) = query_name(&buffer[..length as usize]) else {
            continue;
        };
        if known.len() >= MAX_KNOWN_NAMES {
            known.clear();
        }
        if known.insert(name.clone())
            && let Err(error) = output.write_all(format!("{name}\n").as_bytes())
        {
            return error;
        }
    }
}

fn query_name(packet: &[u8]) -> Option<String> {
    let packet = parse_udp(packet)?;
    if packet.destination.port() != DNS_PORT {
        return None;
    }
    // A query can carry any byte, a line break included, which must not reach the file.
    hostname::normalize(&parse_query(packet.payload)?.name, false)
}
