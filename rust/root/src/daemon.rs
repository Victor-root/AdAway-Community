//! Leaving the app shell: a root process outlives the app once detached.

use std::fs::File;
use std::io;
use std::os::fd::AsRawFd;

/// The parent ends, telling the app the process started; the child goes on in its own session,
/// its standard streams closed. To be called before any other thread exists.
pub fn detach() -> io::Result<()> {
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
