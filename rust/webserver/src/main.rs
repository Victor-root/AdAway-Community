//! Started as root by the app: binds the ports, then goes on in the background, detached from the
//! app shell. Until then, failures end it with an exit code the app tells the user about.
//!
//! Usage: `adaway-webserver --resources <directory> [--icon]`. The directory holds `test.html`,
//! `icon.svg`, `certificate.pem` and `key.pem`.

use std::fs::{self, File};
use std::io;
use std::os::fd::AsRawFd;
use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::sync::Arc;

use adaway_webserver::http::Content;
use adaway_webserver::server;
use rustls::ServerConfig;
use rustls::pki_types::pem::PemObject;
use rustls::pki_types::{CertificateDer, PrivateKeyDer};

/// Exit codes, mirrored in the app.
const EXIT_USAGE: u8 = 1;
const EXIT_RESOURCES: u8 = 2;
const EXIT_ADDRESS_IN_USE: u8 = 3;
const EXIT_START: u8 = 4;

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
    let (resources, icon) = parse_arguments().ok_or((
        EXIT_USAGE,
        "usage: --resources <directory> [--icon]".to_owned(),
    ))?;
    let content = Content {
        test_page: read(&resources.join("test.html"))?,
        icon: if icon {
            Some(read(&resources.join("icon.svg"))?)
        } else {
            None
        },
    };
    let tls = tls_config(&resources).map_err(|message| (EXIT_RESOURCES, message))?;
    let listeners = server::bind().map_err(|error| {
        let code = if error.kind() == io::ErrorKind::AddrInUse {
            EXIT_ADDRESS_IN_USE
        } else {
            EXIT_START
        };
        (code, format!("ports not bound: {error}"))
    })?;
    daemonize().map_err(|error| (EXIT_START, format!("not detached: {error}")))?;
    protect_from_low_memory_killer();
    server::serve(listeners, Arc::new(content), Arc::new(tls));
    Err((EXIT_START, "every listener failed".to_owned()))
}

fn parse_arguments() -> Option<(PathBuf, bool)> {
    let mut resources = None;
    let mut icon = false;
    let mut arguments = std::env::args().skip(1);
    while let Some(argument) = arguments.next() {
        match argument.as_str() {
            "--resources" => resources = Some(PathBuf::from(arguments.next()?)),
            "--icon" => icon = true,
            _ => return None,
        }
    }
    Some((resources?, icon))
}

fn read(path: &Path) -> Result<Vec<u8>, (u8, String)> {
    fs::read(path).map_err(|error| {
        (
            EXIT_RESOURCES,
            format!("{} not read: {error}", path.display()),
        )
    })
}

fn tls_config(resources: &Path) -> Result<ServerConfig, String> {
    let certificates = CertificateDer::pem_file_iter(resources.join("certificate.pem"))
        .and_then(Iterator::collect::<Result<Vec<_>, _>>)
        .map_err(|error| format!("certificate not read: {error}"))?;
    let key = PrivateKeyDer::from_pem_file(resources.join("key.pem"))
        .map_err(|error| format!("key not read: {error}"))?;
    ServerConfig::builder_with_provider(Arc::new(rustls::crypto::ring::default_provider()))
        .with_safe_default_protocol_versions()
        .and_then(|builder| {
            builder
                .with_no_client_auth()
                .with_single_cert(certificates, key)
        })
        .map_err(|error| format!("TLS not configured: {error}"))
}

/// The parent ends, telling the app the server started; the child goes on in its own session,
/// so that it outlives the app shell.
fn daemonize() -> io::Result<()> {
    // SAFETY: no other thread exists yet, so the child gets a consistent copy of the process.
    match unsafe { libc::fork() } {
        -1 => Err(io::Error::last_os_error()),
        0 => {
            // SAFETY: plain system calls on descriptors this process owns.
            unsafe { libc::setsid() };
            let null = File::options().read(true).write(true).open("/dev/null")?;
            for descriptor in 0..=2 {
                // SAFETY: as above.
                unsafe { libc::dup2(null.as_raw_fd(), descriptor) };
            }
            Ok(())
        }
        _ => std::process::exit(0),
    }
}

/// Android would otherwise kill it first when memory runs low.
fn protect_from_low_memory_killer() {
    let _ = fs::write("/proc/self/oom_score_adj", "-1000");
}
