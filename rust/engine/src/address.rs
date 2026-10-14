//! IP address rules for redirections.

use std::net::IpAddr;

/// Returns the canonical form of an IPv4 or IPv6 address, or `None` if `input` is not one.
pub fn normalize_ip(input: &str) -> Option<String> {
    input
        .trim()
        .parse::<IpAddr>()
        .ok()
        .map(|address| address.to_string())
}

#[cfg(test)]
mod tests {
    use super::normalize_ip;

    #[test]
    fn accepts_and_canonicalizes_addresses() {
        assert_eq!(
            normalize_ip(" 192.168.1.10 ").as_deref(),
            Some("192.168.1.10")
        );
        assert_eq!(normalize_ip("::1").as_deref(), Some("::1"));
        assert_eq!(
            normalize_ip("2001:DB8:0:0:0:0:0:1").as_deref(),
            Some("2001:db8::1")
        );
    }

    #[test]
    fn rejects_non_addresses() {
        for input in [
            "",
            "localhost",
            "1.2.3",
            "1.2.3.4.5",
            "256.1.1.1",
            "01.2.3.4",
            "fe80::1%wlan0",
        ] {
            assert!(normalize_ip(input).is_none(), "{input} should be invalid");
        }
    }
}
