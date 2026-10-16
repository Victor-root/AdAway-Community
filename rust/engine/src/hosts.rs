//! The hosts file installed by the root blocking, written from the final list.
//!
//! After the given header comes a blank line, the localhost entries, then the entries sorted by
//! name: a blocked name points to the IPv4 redirection, and also to the IPv6 one when given; a
//! redirected name points to its own address only. Wildcard rules have no hosts file form.

use std::io::{self, Write};
use std::path::Path;

use crate::rule;

pub struct HostsOptions<'a> {
    /// Comment lines, written as given.
    pub header: &'a [String],
    pub ipv4_redirection: &'a str,
    pub ipv6_redirection: Option<&'a str>,
}

const LOCALHOST: [&str; 2] = ["127.0.0.1 localhost", "::1 localhost"];

/// Writes the hosts file at `output` from the final list in `final_directory`. The blocked names
/// are streamed from their file, so the memory it takes does not grow with their number.
pub fn write(final_directory: &Path, output: &Path, options: &HostsOptions) -> io::Result<()> {
    let redirected: Vec<(String, String)> =
        rule::read_lines(&final_directory.join("redirected.txt"))?
            .into_iter()
            .filter_map(|line| {
                let (host, address) = line.split_once(' ')?;
                Some((host.to_owned(), address.to_owned()))
            })
            .collect();
    let blocked = rule::lines(&final_directory.join("blocked.txt"))?;
    rule::write_with(output, |writer| {
        for line in options
            .header
            .iter()
            .map(String::as_str)
            .chain([""])
            .chain(LOCALHOST)
        {
            writeln!(writer, "{line}")?;
        }
        // Both lists are sorted by name: merge them.
        let mut redirect = redirected.iter().peekable();
        for host in blocked {
            let host = host?;
            while let Some((redirected_host, address)) =
                redirect.next_if(|(redirected_host, _)| redirected_host.as_str() <= host.as_str())
            {
                writeln!(writer, "{address} {redirected_host}")?;
            }
            writeln!(writer, "{} {host}", options.ipv4_redirection)?;
            if let Some(ipv6) = options.ipv6_redirection {
                writeln!(writer, "{ipv6} {host}")?;
            }
        }
        for (host, address) in redirect {
            writeln!(writer, "{address} {host}")?;
        }
        Ok(())
    })
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
