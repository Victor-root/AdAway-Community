//! The address of a DNS over HTTPS server.

use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DohUrl {
    pub host: String,
    pub port: u16,
    /// With its query, if any.
    pub path: String,
}

#[derive(Debug, PartialEq, Eq)]
pub struct InvalidUrl;

impl fmt::Display for InvalidUrl {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("not an https:// address with a host name")
    }
}

impl std::error::Error for InvalidUrl {}

impl DohUrl {
    /// Accepts `https://host[:port][/path]`, the host a name or an IPv4 address, the path
    /// defaulting to `/dns-query`.
    pub fn parse(url: &str) -> Result<Self, InvalidUrl> {
        let rest = url.trim().strip_prefix("https://").ok_or(InvalidUrl)?;
        let (authority, path) = match rest.find(['/', '?']) {
            Some(index) => (&rest[..index], &rest[index..]),
            None => (rest, ""),
        };
        let (host, port) = match authority.split_once(':') {
            Some((host, port)) => (host, port.parse().map_err(|_| InvalidUrl)?),
            None => (authority, 443),
        };
        let valid_host = !host.is_empty()
            && host.chars().all(|character| {
                character.is_ascii_alphanumeric() || matches!(character, '.' | '-')
            });
        if !valid_host {
            return Err(InvalidUrl);
        }
        let path = if path.is_empty() {
            "/dns-query".to_owned()
        } else if path.starts_with('?') {
            format!("/{path}")
        } else {
            path.to_owned()
        };
        if path
            .chars()
            .any(|character| character.is_ascii_control() || character == ' ')
        {
            return Err(InvalidUrl);
        }
        Ok(DohUrl {
            host: host.to_ascii_lowercase(),
            port,
            path,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::{DohUrl, InvalidUrl};

    #[test]
    fn reads_the_host_port_and_path() {
        assert_eq!(
            DohUrl::parse("https://cloudflare-dns.com/dns-query"),
            Ok(DohUrl {
                host: "cloudflare-dns.com".to_owned(),
                port: 443,
                path: "/dns-query".to_owned()
            })
        );
        assert_eq!(
            DohUrl::parse("https://Dns.Example:8443/resolve?x=1").unwrap(),
            DohUrl {
                host: "dns.example".to_owned(),
                port: 8443,
                path: "/resolve?x=1".to_owned()
            }
        );
        assert_eq!(
            DohUrl::parse("https://dns.google").unwrap().path,
            "/dns-query"
        );
    }

    #[test]
    fn refuses_other_addresses() {
        assert_eq!(
            DohUrl::parse("http://dns.google/dns-query"),
            Err(InvalidUrl)
        );
        assert_eq!(DohUrl::parse("https:///dns-query"), Err(InvalidUrl));
        assert_eq!(DohUrl::parse("https://dns.google:port/"), Err(InvalidUrl));
        assert_eq!(DohUrl::parse("https://bad host/"), Err(InvalidUrl));
    }
}
