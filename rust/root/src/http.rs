//! The answer to every request: an empty page, the app icon in place of the ads when asked, or
//! the test page the app opens to check the server.

use std::io::{self, Read, Write};

/// The path of the test page.
pub const TEST_PATH: &str = "/internal-test";
/// Past this size, the request head is not read further.
const MAX_HEAD: usize = 8 * 1024;

pub struct Content {
    pub test_page: Vec<u8>,
    /// Served for every other path when set.
    pub icon: Option<Vec<u8>>,
}

/// Reads one request and answers it. The connection is then to be closed.
pub fn answer<S: Read + Write>(stream: &mut S, content: &Content) -> io::Result<()> {
    let Some((method, path)) = read_request_line(stream)? else {
        return Ok(());
    };
    stream.write_all(&response(&method, &path, content))?;
    stream.flush()
}

/// Reads the request head, and returns its method and path.
fn read_request_line<S: Read>(stream: &mut S) -> io::Result<Option<(String, String)>> {
    let mut head = Vec::new();
    let mut buffer = [0; 1024];
    while !head.windows(4).any(|window| window == b"\r\n\r\n") {
        let read = stream.read(&mut buffer)?;
        if read == 0 || head.len() + read > MAX_HEAD {
            return Ok(None);
        }
        head.extend_from_slice(&buffer[..read]);
    }
    let line = head.split(|&byte| byte == b'\r').next().unwrap_or_default();
    let mut parts = std::str::from_utf8(line).unwrap_or_default().split(' ');
    match (parts.next(), parts.next()) {
        (Some(method), Some(target)) if !method.is_empty() => {
            // The query does not change the answer.
            let path = target.split('?').next().unwrap_or_default();
            Ok(Some((method.to_owned(), path.to_owned())))
        }
        _ => Ok(None),
    }
}

fn response(method: &str, path: &str, content: &Content) -> Vec<u8> {
    let (body, content_type): (&[u8], _) = if path == TEST_PATH {
        (&content.test_page, Some("text/html; charset=utf-8"))
    } else if let Some(icon) = &content.icon {
        (icon, Some("image/svg+xml"))
    } else {
        (&[], None)
    };
    let mut response = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\n", body.len());
    if let Some(content_type) = content_type {
        response.push_str(&format!("Content-Type: {content_type}\r\n"));
    }
    response.push_str("Cache-Control: no-store\r\nConnection: close\r\n\r\n");
    let mut bytes = response.into_bytes();
    if method != "HEAD" {
        bytes.extend_from_slice(body);
    }
    bytes
}

#[cfg(test)]
mod tests {
    use super::{Content, answer};
    use std::io::{self, Cursor, Read, Write};

    /// A request to read, and what is written back.
    struct Exchange {
        request: Cursor<Vec<u8>>,
        written: Vec<u8>,
    }

    impl Read for Exchange {
        fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
            self.request.read(buffer)
        }
    }

    impl Write for Exchange {
        fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
            self.written.write(buffer)
        }

        fn flush(&mut self) -> io::Result<()> {
            Ok(())
        }
    }

    fn exchange(request: &str, icon: bool) -> String {
        let content = Content {
            test_page: b"<html>test</html>".to_vec(),
            icon: icon.then(|| b"<svg/>".to_vec()),
        };
        let mut exchange = Exchange {
            request: Cursor::new(request.as_bytes().to_vec()),
            written: Vec::new(),
        };
        answer(&mut exchange, &content).unwrap();
        String::from_utf8(exchange.written).unwrap()
    }

    #[test]
    fn answers_an_empty_page_by_default() {
        let response = exchange(
            "GET /ad.js HTTP/1.1\r\nHost: ads.example.com\r\n\r\n",
            false,
        );
        assert!(response.starts_with("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n"));
        assert!(response.ends_with("\r\n\r\n"));
    }

    #[test]
    fn answers_the_icon_when_asked_and_the_test_page() {
        assert!(
            exchange("GET /banner.png HTTP/1.1\r\n\r\n", true).ends_with(
                "image/svg+xml\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n<svg/>"
            )
        );
        assert!(
            exchange("GET /internal-test?x=1 HTTP/1.1\r\n\r\n", true)
                .ends_with("<html>test</html>")
        );
        assert!(exchange("HEAD /internal-test HTTP/1.1\r\n\r\n", false).ends_with("\r\n\r\n"));
    }

    #[test]
    fn ignores_an_incomplete_request() {
        assert!(exchange("GET / HTTP/1.1\r\n", false).is_empty());
    }
}
