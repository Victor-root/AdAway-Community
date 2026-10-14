//! The parts of DNS messages the filter needs: the first question of a query, and the local
//! answers for blocked and redirected names.

use std::net::IpAddr;

const HEADER: usize = 12;
const MAX_NAME_LENGTH: usize = 253;
const TYPE_A: u16 = 1;
const TYPE_AAAA: u16 = 28;
const TYPE_SOA: u16 = 6;
const CLASS_IN: u16 = 1;
/// Short, so that a name the user unblocks works again almost at once.
const ANSWER_TTL: u32 = 5;
/// Compression pointer to the question name, right after the header.
const QUESTION_NAME_POINTER: [u8; 2] = [0xc0, 0x0c];
/// Wire form of `adaway.vpn.invalid.`, a name that can never exist.
const SOA_NAME: &[u8] = b"\x06adaway\x03vpn\x07invalid\x00";

#[derive(Debug, PartialEq, Eq)]
pub struct Query<'a> {
    pub id: u16,
    /// Lowercase, without the final dot.
    pub name: String,
    pub record_type: u16,
    message: &'a [u8],
    question_end: usize,
}

/// Reads a standard query and its first question. Anything else gives `None`.
pub fn parse_query(message: &[u8]) -> Option<Query<'_>> {
    if message.len() < HEADER {
        return None;
    }
    let flags = u16::from_be_bytes([message[2], message[3]]);
    let is_response = flags & 0x8000 != 0;
    let opcode = (flags >> 11) & 0x0f;
    let questions = u16::from_be_bytes([message[4], message[5]]);
    if is_response || opcode != 0 || questions == 0 {
        return None;
    }
    let (name, name_end) = read_name(message, HEADER)?;
    let question_end = name_end + 4;
    let fixed = message.get(name_end..question_end)?;
    Some(Query {
        id: u16::from_be_bytes([message[0], message[1]]),
        name,
        record_type: u16::from_be_bytes([fixed[0], fixed[1]]),
        message,
        question_end,
    })
}

/// Reads an uncompressed name, as questions are written.
fn read_name(message: &[u8], start: usize) -> Option<(String, usize)> {
    let mut name = String::new();
    let mut position = start;
    loop {
        let length = usize::from(*message.get(position)?);
        position += 1;
        if length == 0 {
            return Some((name, position));
        }
        if length > 63 {
            return None;
        }
        let label = message.get(position..position + length)?;
        if !name.is_empty() {
            name.push('.');
        }
        name.extend(label.iter().map(|byte| byte.to_ascii_lowercase() as char));
        if name.len() > MAX_NAME_LENGTH {
            return None;
        }
        position += length;
    }
}

impl Query<'_> {
    /// "This name has no record of this type", with a short negative caching time.
    pub fn blocked_response(&self) -> Vec<u8> {
        let mut response = self.response_header(0, 1, false);
        response.extend_from_slice(&QUESTION_NAME_POINTER);
        response.extend_from_slice(&TYPE_SOA.to_be_bytes());
        response.extend_from_slice(&CLASS_IN.to_be_bytes());
        response.extend_from_slice(&ANSWER_TTL.to_be_bytes());
        let mut soa = Vec::new();
        soa.extend_from_slice(SOA_NAME);
        soa.extend_from_slice(SOA_NAME);
        // Serial, refresh, retry, expire, then the negative caching time.
        for value in [0u32, 0, 0, 0, ANSWER_TTL] {
            soa.extend_from_slice(&value.to_be_bytes());
        }
        response.extend_from_slice(&(soa.len() as u16).to_be_bytes());
        response.extend_from_slice(&soa);
        response
    }

    /// Answers with `address` when the question asks for its family, and as blocked otherwise.
    pub fn redirected_response(&self, address: IpAddr) -> Vec<u8> {
        let (record_type, data) = match address {
            IpAddr::V4(address) => (TYPE_A, address.octets().to_vec()),
            IpAddr::V6(address) => (TYPE_AAAA, address.octets().to_vec()),
        };
        if self.record_type != record_type {
            return self.blocked_response();
        }
        let mut response = self.response_header(1, 0, true);
        response.extend_from_slice(&QUESTION_NAME_POINTER);
        response.extend_from_slice(&record_type.to_be_bytes());
        response.extend_from_slice(&CLASS_IN.to_be_bytes());
        response.extend_from_slice(&ANSWER_TTL.to_be_bytes());
        response.extend_from_slice(&(data.len() as u16).to_be_bytes());
        response.extend_from_slice(&data);
        response
    }

    /// Header and question of a response with the given record counts.
    fn response_header(&self, answers: u16, authorities: u16, authoritative: bool) -> Vec<u8> {
        let query_flags = u16::from_be_bytes([self.message[2], self.message[3]]);
        let recursion_desired = query_flags & 0x0100;
        let flags = 0x8000 | recursion_desired | 0x0080 | if authoritative { 0x0400 } else { 0 };
        let mut response = Vec::with_capacity(self.question_end + 64);
        response.extend_from_slice(&self.id.to_be_bytes());
        response.extend_from_slice(&flags.to_be_bytes());
        for count in [1, answers, authorities, 0] {
            response.extend_from_slice(&count.to_be_bytes());
        }
        response.extend_from_slice(&self.message[HEADER..self.question_end]);
        response
    }
}

/// Reads the identifier of a DNS message.
pub fn message_id(message: &[u8]) -> Option<u16> {
    Some(u16::from_be_bytes([*message.first()?, *message.get(1)?]))
}

/// Replaces the identifier of a DNS message.
pub fn set_message_id(message: &mut [u8], id: u16) {
    if message.len() >= 2 {
        message[..2].copy_from_slice(&id.to_be_bytes());
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use super::{TYPE_A, TYPE_AAAA, message_id, parse_query, set_message_id};
    use std::net::IpAddr;

    /// A query with recursion desired and an EDNS record, as Android sends them.
    pub(crate) fn query(name: &str, record_type: u16) -> Vec<u8> {
        let mut message = vec![0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 1];
        for label in name.split('.') {
            message.push(label.len() as u8);
            message.extend_from_slice(label.as_bytes());
        }
        message.push(0);
        message.extend_from_slice(&record_type.to_be_bytes());
        message.extend_from_slice(&[0, 1]);
        message.extend_from_slice(&[0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0]);
        message
    }

    fn u16_at(message: &[u8], index: usize) -> u16 {
        u16::from_be_bytes([message[index], message[index + 1]])
    }

    #[test]
    fn reads_the_first_question() {
        let message = query("Ads.Example.COM", TYPE_AAAA);
        let parsed = parse_query(&message).unwrap();
        assert_eq!(parsed.id, 0x1234);
        assert_eq!(parsed.name, "ads.example.com");
        assert_eq!(parsed.record_type, TYPE_AAAA);
    }

    #[test]
    fn ignores_responses_and_other_operations() {
        let mut response = query("example.com", TYPE_A);
        response[2] |= 0x80;
        assert!(parse_query(&response).is_none());
        let mut update = query("example.com", TYPE_A);
        update[2] |= 5 << 3;
        assert!(parse_query(&update).is_none());
        let mut compressed = query("example.com", TYPE_A);
        compressed[12] = 0xc0;
        assert!(parse_query(&compressed).is_none());
        assert!(parse_query(&query("example.com", TYPE_A)[..20]).is_none());
    }

    #[test]
    fn answers_blocked_names_with_no_data() {
        let message = query("ads.example.com", TYPE_A);
        let response = parse_query(&message).unwrap().blocked_response();
        assert_eq!(message_id(&response), Some(0x1234));
        assert_eq!(
            u16_at(&response, 2),
            0x8180,
            "response, recursion desired and available, no error"
        );
        assert_eq!(
            [
                u16_at(&response, 4),
                u16_at(&response, 6),
                u16_at(&response, 8),
                u16_at(&response, 10)
            ],
            [1, 0, 1, 0]
        );
        let question_end = 12 + 17 + 4;
        assert_eq!(&response[12..question_end], &message[12..question_end]);
        assert_eq!(u16_at(&response, question_end + 2), 6, "SOA record");
    }

    #[test]
    fn answers_redirections_for_the_matching_family_only() {
        let address: IpAddr = "192.168.1.10".parse().unwrap();
        let a = parse_query(&query("nas.lan", TYPE_A))
            .unwrap()
            .redirected_response(address);
        assert_eq!(u16_at(&a, 6), 1, "one answer");
        assert_eq!(&a[a.len() - 4..], &[192, 168, 1, 10]);
        let aaaa = parse_query(&query("nas.lan", TYPE_AAAA))
            .unwrap()
            .redirected_response(address);
        assert_eq!(
            [u16_at(&aaaa, 6), u16_at(&aaaa, 8)],
            [0, 1],
            "no data for another family"
        );
    }

    #[test]
    fn rewrites_identifiers() {
        let mut message = query("example.com", TYPE_A);
        set_message_id(&mut message, 7);
        assert_eq!(message_id(&message), Some(7));
        assert_eq!(message_id(&[1]), None);
    }

    #[test]
    fn survives_mutated_queries() {
        let original = query("ads.example.com", TYPE_A);
        let mut state = 0x9e37_79b9_7f4a_7c15u64;
        for _ in 0..20_000 {
            let mut message = original.clone();
            for _ in 0..4 {
                state ^= state << 13;
                state ^= state >> 7;
                state ^= state << 17;
                let index = (state as usize) % message.len();
                message[index] = (state >> 32) as u8;
            }
            message.truncate((state as usize >> 8) % (original.len() + 1));
            if let Some(parsed) = parse_query(&message) {
                let _ = parsed.blocked_response();
                let _ = parsed.redirected_response("10.0.0.1".parse().unwrap());
            }
        }
    }
}
