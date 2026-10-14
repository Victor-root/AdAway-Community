//! The root processes the app starts: the local web server, to which blocked hosts point so that
//! apps get an answer at once instead of waiting for a connection that never comes, and the DNS
//! capture of the DNS log.

pub mod certificate;
pub mod daemon;
pub mod http;
pub mod server;
