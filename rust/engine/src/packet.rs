//! IPv4 and IPv6 UDP packets as read from and written to the VPN tunnel.

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};

const IPV4_HEADER: usize = 20;
const IPV6_HEADER: usize = 40;
const UDP_HEADER: usize = 8;
const PROTOCOL_UDP: u8 = 17;
const TTL: u8 = 64;

#[derive(Debug, PartialEq, Eq)]
pub struct UdpPacket<'a> {
    pub source: SocketAddr,
    pub destination: SocketAddr,
    pub payload: &'a [u8],
}

/// Reads an unfragmented UDP packet. Anything else (other protocols, fragments, IPv6 extension
/// headers, inconsistent lengths) gives `None`.
pub fn parse_udp(packet: &[u8]) -> Option<UdpPacket<'_>> {
    let (source, destination, segment) = match packet.first()? >> 4 {
        4 => parse_ipv4(packet)?,
        6 => parse_ipv6(packet)?,
        _ => return None,
    };
    if segment.len() < UDP_HEADER {
        return None;
    }
    let udp_length = usize::from(u16::from_be_bytes([segment[4], segment[5]]));
    if udp_length < UDP_HEADER || udp_length > segment.len() {
        return None;
    }
    Some(UdpPacket {
        source: SocketAddr::new(source, u16::from_be_bytes([segment[0], segment[1]])),
        destination: SocketAddr::new(destination, u16::from_be_bytes([segment[2], segment[3]])),
        payload: &segment[UDP_HEADER..udp_length],
    })
}

fn parse_ipv4(packet: &[u8]) -> Option<(IpAddr, IpAddr, &[u8])> {
    if packet.len() < IPV4_HEADER {
        return None;
    }
    let header_length = usize::from(packet[0] & 0x0f) * 4;
    let total_length = usize::from(u16::from_be_bytes([packet[2], packet[3]]));
    let fragmented = u16::from_be_bytes([packet[6], packet[7]]) & 0x3fff != 0;
    if header_length < IPV4_HEADER || total_length < header_length || total_length > packet.len() {
        return None;
    }
    if fragmented || packet[9] != PROTOCOL_UDP {
        return None;
    }
    let source = Ipv4Addr::new(packet[12], packet[13], packet[14], packet[15]);
    let destination = Ipv4Addr::new(packet[16], packet[17], packet[18], packet[19]);
    Some((
        source.into(),
        destination.into(),
        &packet[header_length..total_length],
    ))
}

fn parse_ipv6(packet: &[u8]) -> Option<(IpAddr, IpAddr, &[u8])> {
    if packet.len() < IPV6_HEADER || packet[6] != PROTOCOL_UDP {
        return None;
    }
    let payload_length = usize::from(u16::from_be_bytes([packet[4], packet[5]]));
    let end = IPV6_HEADER + payload_length;
    if end > packet.len() {
        return None;
    }
    let source: [u8; 16] = packet[8..24].try_into().ok()?;
    let destination: [u8; 16] = packet[24..40].try_into().ok()?;
    Some((
        Ipv6Addr::from(source).into(),
        Ipv6Addr::from(destination).into(),
        &packet[IPV6_HEADER..end],
    ))
}

/// Builds a UDP packet. Returns `None` when the addresses are not of the same family or the
/// payload does not fit in a packet.
pub fn build_udp(source: SocketAddr, destination: SocketAddr, payload: &[u8]) -> Option<Vec<u8>> {
    let udp_length = u16::try_from(UDP_HEADER + payload.len()).ok()?;
    let mut segment = Vec::with_capacity(usize::from(udp_length));
    segment.extend_from_slice(&source.port().to_be_bytes());
    segment.extend_from_slice(&destination.port().to_be_bytes());
    segment.extend_from_slice(&udp_length.to_be_bytes());
    segment.extend_from_slice(&[0, 0]);
    segment.extend_from_slice(payload);
    match (source.ip(), destination.ip()) {
        (IpAddr::V4(from), IpAddr::V4(to)) => {
            let total_length = u16::try_from(IPV4_HEADER + segment.len()).ok()?;
            let checksum = udp_checksum(&pseudo_header_v4(from, to, udp_length), &segment);
            segment[6..8].copy_from_slice(&checksum.to_be_bytes());
            let mut packet = Vec::with_capacity(usize::from(total_length));
            packet.extend_from_slice(&[0x45, 0]);
            packet.extend_from_slice(&total_length.to_be_bytes());
            packet.extend_from_slice(&[0, 0, 0x40, 0, TTL, PROTOCOL_UDP, 0, 0]);
            packet.extend_from_slice(&from.octets());
            packet.extend_from_slice(&to.octets());
            let header_checksum = !ones_complement_sum(&packet, 0);
            packet[10..12].copy_from_slice(&header_checksum.to_be_bytes());
            packet.extend_from_slice(&segment);
            Some(packet)
        }
        (IpAddr::V6(from), IpAddr::V6(to)) => {
            let checksum = udp_checksum(&pseudo_header_v6(from, to, udp_length), &segment);
            segment[6..8].copy_from_slice(&checksum.to_be_bytes());
            let mut packet = Vec::with_capacity(IPV6_HEADER + segment.len());
            packet.extend_from_slice(&[0x60, 0, 0, 0]);
            packet.extend_from_slice(&udp_length.to_be_bytes());
            packet.extend_from_slice(&[PROTOCOL_UDP, TTL]);
            packet.extend_from_slice(&from.octets());
            packet.extend_from_slice(&to.octets());
            packet.extend_from_slice(&segment);
            Some(packet)
        }
        _ => None,
    }
}

fn pseudo_header_v4(source: Ipv4Addr, destination: Ipv4Addr, udp_length: u16) -> Vec<u8> {
    let mut header = Vec::with_capacity(12);
    header.extend_from_slice(&source.octets());
    header.extend_from_slice(&destination.octets());
    header.extend_from_slice(&[0, PROTOCOL_UDP]);
    header.extend_from_slice(&udp_length.to_be_bytes());
    header
}

fn pseudo_header_v6(source: Ipv6Addr, destination: Ipv6Addr, udp_length: u16) -> Vec<u8> {
    let mut header = Vec::with_capacity(40);
    header.extend_from_slice(&source.octets());
    header.extend_from_slice(&destination.octets());
    header.extend_from_slice(&u32::from(udp_length).to_be_bytes());
    header.extend_from_slice(&[0, 0, 0, PROTOCOL_UDP]);
    header
}

/// UDP checksum, where 0 is sent as `0xffff` since 0 means "no checksum".
fn udp_checksum(pseudo_header: &[u8], segment: &[u8]) -> u16 {
    let sum = ones_complement_sum(segment, ones_complement_sum(pseudo_header, 0));
    match !sum {
        0 => 0xffff,
        checksum => checksum,
    }
}

/// Folded 16-bit one's complement sum of `data`, continuing from `initial`.
fn ones_complement_sum(data: &[u8], initial: u16) -> u16 {
    let mut sum = u32::from(initial);
    for chunk in data.chunks(2) {
        let word = u16::from_be_bytes([chunk[0], chunk.get(1).copied().unwrap_or(0)]);
        sum += u32::from(word);
    }
    while sum > 0xffff {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    sum as u16
}

#[cfg(test)]
mod tests {
    use super::{build_udp, ones_complement_sum, parse_udp};
    use std::net::SocketAddr;

    fn address(text: &str) -> SocketAddr {
        text.parse().unwrap()
    }

    #[test]
    fn round_trips_ipv4_packets() {
        let packet =
            build_udp(address("10.0.0.2:40000"), address("192.0.2.2:53"), b"query").unwrap();
        assert_eq!(
            ones_complement_sum(&packet[..20], 0),
            0xffff,
            "valid IPv4 header checksum"
        );
        let parsed = parse_udp(&packet).unwrap();
        assert_eq!(parsed.source, address("10.0.0.2:40000"));
        assert_eq!(parsed.destination, address("192.0.2.2:53"));
        assert_eq!(parsed.payload, b"query");
    }

    #[test]
    fn round_trips_ipv6_packets() {
        let packet = build_udp(
            address("[fd00::2]:40000"),
            address("[2001:db8::2]:53"),
            b"query",
        )
        .unwrap();
        let parsed = parse_udp(&packet).unwrap();
        assert_eq!(parsed.source, address("[fd00::2]:40000"));
        assert_eq!(parsed.payload, b"query");
    }

    #[test]
    fn computes_the_udp_checksum() {
        // Checked with the pseudo header sum: a valid segment sums to 0xffff.
        let packet = build_udp(address("10.0.0.2:40000"), address("192.0.2.2:53"), b"odd").unwrap();
        let mut pseudo = Vec::new();
        pseudo.extend_from_slice(&packet[12..20]);
        pseudo.extend_from_slice(&[0, 17, 0, 11]);
        assert_eq!(
            ones_complement_sum(&packet[20..], ones_complement_sum(&pseudo, 0)),
            0xffff
        );
    }

    #[test]
    fn rejects_mixed_families() {
        assert!(build_udp(address("10.0.0.2:1"), address("[::1]:53"), b"").is_none());
    }

    #[test]
    fn rejects_malformed_packets() {
        let valid =
            build_udp(address("10.0.0.2:40000"), address("192.0.2.2:53"), b"query").unwrap();
        assert!(parse_udp(&[]).is_none());
        assert!(parse_udp(&valid[..10]).is_none());
        let mut tcp = valid.clone();
        tcp[9] = 6;
        assert!(parse_udp(&tcp).is_none());
        let mut fragment = valid.clone();
        fragment[6] = 0x20;
        assert!(parse_udp(&fragment).is_none());
        let mut long_udp = valid.clone();
        long_udp[25] = 0xff;
        assert!(parse_udp(&long_udp).is_none());
        let mut long_ip = valid;
        long_ip[3] = 0xff;
        assert!(parse_udp(&long_ip).is_none());
    }

    #[test]
    fn survives_random_input() {
        let mut state = 0x2545_f491_4f6c_dd1du64;
        for length in 0..2000usize {
            let bytes: Vec<u8> = (0..length % 96)
                .map(|_| {
                    state ^= state << 13;
                    state ^= state >> 7;
                    state ^= state << 17;
                    (state >> 24) as u8
                })
                .collect();
            let _ = parse_udp(&bytes);
        }
    }
}
