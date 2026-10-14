//! The local web server of the root blocking: blocked hosts point to the device itself, where it
//! answers at once instead of letting the apps wait for a connection that never comes.

pub mod certificate;
pub mod http;
pub mod server;
