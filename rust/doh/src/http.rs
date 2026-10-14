//! The HTTP/1.1 exchange of DNS over HTTPS (RFC 8484): a POST of the query, its answer in the body.

use std::io::{self, BufRead, Read, Write};

use crate::url::DohUrl;

const MESSAGE_TYPE: &str = "application/dns-message";
/// The largest DNS message.
const MAX_BODY: usize = 65_535;
const MAX_LINE: u64 = 8 * 1024;

pub struct Answer {
    pub message: Vec<u8>,
    /// Whether the server keeps the connection open for the next query.
    pub keep_alive: bool,
}

pub fn write_query<W: Write>(stream: &mut W, url: &DohUrl, query: &[u8]) -> io::Result<()> {
    let head = format!(
        "POST {} HTTP/1.1\r\nHost: {}\r\nContent-Type: {MESSAGE_TYPE}\r\nAccept: {MESSAGE_TYPE}\r\nContent-Length: {}\r\n\r\n",
        url.path,
        url.host,
        query.len()
    );
    stream.write_all(head.as_bytes())?;
    stream.write_all(query)?;
    stream.flush()
}

pub fn read_answer<R: BufRead>(stream: &mut R) -> io::Result<Answer> {
    let status = read_line(stream)?;
    let mut parts = status.split(' ');
    let version = parts.next().unwrap_or_default();
    if !version.starts_with("HTTP/1.") {
        return Err(invalid("not an HTTP/1 answer"));
    }
    let mut keep_alive = version == "HTTP/1.1";
    let ok = parts.next() == Some("200");
    let mut length = None;
    let mut chunked = false;
    loop {
        let line = read_line(stream)?;
        if line.is_empty() {
            break;
        }
        let Some((name, value)) = line.split_once(':') else {
            continue;
        };
        let value = value.trim();
        match name.trim().to_ascii_lowercase().as_str() {
            "content-length" => {
                length = Some(value.parse::<usize>().map_err(|_| invalid("bad length"))?)
            }
            "transfer-encoding" => chunked = value.eq_ignore_ascii_case("chunked"),
            "connection" => keep_alive = !value.eq_ignore_ascii_case("close"),
            _ => {}
        }
    }
    let message = match (chunked, length) {
        (true, _) => read_chunked(stream)?,
        (false, Some(length)) if length <= MAX_BODY => {
            let mut body = vec![0; length];
            stream.read_exact(&mut body)?;
            body
        }
        (false, Some(_)) => return Err(invalid("answer too large")),
        (false, None) => {
            keep_alive = false;
            let mut body = Vec::new();
            stream.take(MAX_BODY as u64 + 1).read_to_end(&mut body)?;
            body
        }
    };
    if !ok {
        return Err(invalid(&format!("the server answered: {status}")));
    }
    if message.len() > MAX_BODY {
        return Err(invalid("answer too large"));
    }
    Ok(Answer {
        message,
        keep_alive,
    })
}

fn read_chunked<R: BufRead>(stream: &mut R) -> io::Result<Vec<u8>> {
    let mut body = Vec::new();
    loop {
        let line = read_line(stream)?;
        let size = usize::from_str_radix(line.split(';').next().unwrap_or_default().trim(), 16)
            .map_err(|_| invalid("bad chunk size"))?;
        if size == 0 {
            // Trailers, up to the empty line.
            while !read_line(stream)?.is_empty() {}
            return Ok(body);
        }
        if body.len() + size > MAX_BODY {
            return Err(invalid("answer too large"));
        }
        let start = body.len();
        body.resize(start + size, 0);
        stream.read_exact(&mut body[start..])?;
        if !read_line(stream)?.is_empty() {
            return Err(invalid("bad chunk end"));
        }
    }
}

/// A line without its end, which must come within [`MAX_LINE`] bytes.
fn read_line<R: BufRead>(stream: &mut R) -> io::Result<String> {
    let mut line = String::new();
    stream.take(MAX_LINE).read_line(&mut line)?;
    if !line.ends_with('\n') {
        return Err(io::Error::new(
            io::ErrorKind::UnexpectedEof,
            "incomplete line",
        ));
    }
    Ok(line.trim_end_matches(['\r', '\n']).to_owned())
}

fn invalid(reason: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, reason.to_owned())
}

#[cfg(test)]
mod tests {
    use super::{read_answer, write_query};
    use crate::url::DohUrl;
    use std::io::Cursor;

    #[test]
    fn posts_the_query() {
        let mut written = Vec::new();
        write_query(
            &mut written,
            &DohUrl::parse("https://dns.example/q").unwrap(),
            b"\x12\x34",
        )
        .unwrap();
        assert_eq!(
            written,
            b"POST /q HTTP/1.1\r\nHost: dns.example\r\nContent-Type: application/dns-message\r\n\
              Accept: application/dns-message\r\nContent-Length: 2\r\n\r\n\x12\x34"
        );
    }

    #[test]
    fn reads_an_answer_with_a_length_or_in_chunks() {
        let answer = read_answer(&mut Cursor::new(
            &b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabcNEXT"[..],
        ))
        .unwrap();
        assert_eq!(answer.message, b"abc");
        assert!(answer.keep_alive);

        let chunked = b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n2\r\nab\r\n1;x=y\r\nc\r\n0\r\n\r\n";
        let answer = read_answer(&mut Cursor::new(&chunked[..])).unwrap();
        assert_eq!(answer.message, b"abc");
        assert!(!answer.keep_alive);
    }

    #[test]
    fn refuses_a_failure_or_a_broken_answer() {
        assert!(
            read_answer(&mut Cursor::new(
                &b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"[..]
            ))
            .is_err()
        );
        assert!(
            read_answer(&mut Cursor::new(
                &b"HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\nabc"[..]
            ))
            .is_err()
        );
        assert!(
            read_answer(&mut Cursor::new(
                &b"HTTP/1.1 200 OK\r\nContent-Length: 999999\r\n\r\n"[..]
            ))
            .is_err()
        );
    }
}
