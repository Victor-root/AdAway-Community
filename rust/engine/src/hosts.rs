//! The hosts file installed by the root blocking, written from the final list.
//!
//! After the given header comes a blank line, the localhost entries, then the entries sorted by
//! name: a blocked name points to the IPv4 redirection, and also to the IPv6 one when given; a
//! redirected name points to its own address only. Wildcard rules have no hosts file form.

use std::fs::File;
use std::io::{self, BufRead, BufReader};
use std::path::Path;

use crate::rule;

pub struct HostsOptions<'a> {
    /// Comment lines, written as given.
    pub header: &'a [String],
    pub ipv4_redirection: &'a str,
    pub ipv6_redirection: Option<&'a str>,
}

const LOCALHOST: [&str; 2] = ["127.0.0.1 localhost", "::1 localhost"];

/// Writes the hosts file at `output` from the final list in `final_directory`.
pub fn write(final_directory: &Path, output: &Path, options: &HostsOptions) -> io::Result<()> {
    let blocked = read_lines(&final_directory.join("blocked.txt"))?;
    let redirected: Vec<(String, String)> = read_lines(&final_directory.join("redirected.txt"))?
        .into_iter()
        .filter_map(|line| {
            let (host, address) = line.split_once(' ')?;
            Some((host.to_owned(), address.to_owned()))
        })
        .collect();
    let mut entries = Vec::with_capacity(blocked.len() * 2 + redirected.len());
    // Both lists are sorted by name: merge them.
    let (mut block, mut redirect) = (blocked.iter().peekable(), redirected.iter().peekable());
    loop {
        let take_block = match (block.peek(), redirect.peek()) {
            (Some(host), Some((redirected_host, _))) => host.as_str() < redirected_host.as_str(),
            (Some(_), None) => true,
            (None, Some(_)) => false,
            (None, None) => break,
        };
        if take_block {
            let host = block.next().expect("peeked");
            entries.push(format!("{} {host}", options.ipv4_redirection));
            if let Some(ipv6) = options.ipv6_redirection {
                entries.push(format!("{ipv6} {host}"));
            }
        } else {
            let (host, address) = redirect.next().expect("peeked");
            entries.push(format!("{address} {host}"));
        }
    }
    let lines = options
        .header
        .iter()
        .map(String::as_str)
        .chain([""])
        .chain(LOCALHOST)
        .chain(entries.iter().map(String::as_str));
    rule::write_lines(output, lines)
}

fn read_lines(path: &Path) -> io::Result<Vec<String>> {
    match File::open(path) {
        Ok(file) => BufReader::new(file).lines().collect(),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Vec::new()),
        Err(error) => Err(error),
    }
}

#[cfg(test)]
mod tests {
    use super::{HostsOptions, write};
    use crate::rule;
    use std::fs;

    #[test]
    fn writes_the_entries_sorted_after_the_header() {
        let directory = std::env::temp_dir().join(format!("adaway-hosts-{}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        rule::write_lines(
            &directory.join("blocked.txt"),
            ["ads.example.com", "tracker.net"],
        )
        .unwrap();
        rule::write_lines(
            &directory.join("redirected.txt"),
            ["printer.example.com 192.168.1.10"],
        )
        .unwrap();
        let output = directory.join("hosts");
        let header = vec!["# Generated".to_owned()];
        let options = HostsOptions {
            header: &header,
            ipv4_redirection: "127.0.0.1",
            ipv6_redirection: Some("::1"),
        };

        write(&directory, &output, &options).unwrap();
        assert_eq!(
            fs::read_to_string(&output).unwrap(),
            "# Generated\n\n127.0.0.1 localhost\n::1 localhost\n127.0.0.1 ads.example.com\n::1 ads.example.com\n\
             192.168.1.10 printer.example.com\n127.0.0.1 tracker.net\n::1 tracker.net\n"
        );

        let options = HostsOptions {
            header: &header,
            ipv4_redirection: "0.0.0.0",
            ipv6_redirection: None,
        };
        write(&directory, &output, &options).unwrap();
        assert!(
            fs::read_to_string(&output)
                .unwrap()
                .ends_with("0.0.0.0 tracker.net\n")
        );
        fs::remove_dir_all(&directory).unwrap();
    }
}
