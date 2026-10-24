//! The final list loaded in memory, answering for each queried name.

use std::collections::HashMap;
use std::fs;
use std::io;
use std::net::IpAddr;
use std::path::Path;

use crate::glob::Pattern;
use crate::nameset::NameSet;
use crate::rule::{self, Rule};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Verdict {
    Allow,
    Block,
    Redirect(IpAddr),
}

/// What the compiled final list decides, as described in [`crate::compile`].
#[derive(Default)]
pub struct BlockList {
    blocked: SortedNames,
    redirected: HashMap<Box<str>, IpAddr>,
    user_block_patterns: Vec<Pattern>,
    user_allowed: NameSet,
    /// Domains blocked with their subdomains, and the allow rules of the sources that spare some.
    blocked_domains: SortedNames,
    source_allowed: NameSet,
}

impl BlockList {
    /// Loads the final list written by [`crate::compile::compile`]. A list never computed is empty.
    pub fn load(final_directory: &Path) -> io::Result<Self> {
        let mut list = BlockList {
            blocked: SortedNames::read(&final_directory.join("blocked.txt"))?,
            blocked_domains: SortedNames::read(&final_directory.join("domains.txt"))?,
            ..BlockList::default()
        };
        for pattern in rule::read_lines(&final_directory.join("source_allowed.txt"))? {
            list.source_allowed.insert(pattern);
        }
        for line in rule::read_lines(&final_directory.join("redirected.txt"))? {
            if let Some((host, address)) = line.split_once(' ')
                && let Ok(address) = address.parse()
            {
                list.redirected.insert(host.into(), address);
            }
        }
        for line in rule::read_lines(&final_directory.join("patterns.txt"))? {
            match Rule::from_line(&line) {
                Some(Rule::Blocked(pattern)) => {
                    list.user_block_patterns.push(Pattern::new(pattern))
                }
                Some(Rule::Allowed(pattern)) => list.user_allowed.insert(pattern),
                _ => {}
            }
        }
        Ok(list)
    }

    pub fn verdict(&self, name: &str) -> Verdict {
        if let Some(address) = self.redirected.get(name) {
            Verdict::Redirect(*address)
        } else if self.is_blocked(name) {
            Verdict::Block
        } else {
            Verdict::Allow
        }
    }

    fn is_blocked(&self, name: &str) -> bool {
        self.blocked.contains(name)
            || (self
                .user_block_patterns
                .iter()
                .any(|pattern| pattern.matches(name))
                && !self.user_allowed.matches(name))
            || (self.in_blocked_domain(name)
                && !self.user_allowed.matches(name)
                && !self.source_allowed.matches(name))
    }

    /// Whether `name` is a blocked domain or one of its subdomains.
    fn in_blocked_domain(&self, name: &str) -> bool {
        std::iter::once(name)
            .chain(name.match_indices('.').map(|(index, _)| &name[index + 1..]))
            .any(|domain| self.blocked_domains.contains(domain))
    }
}

/// Millions of names kept in the text of their file, one per line, with where each one starts: a
/// single allocation, about the size of the file, instead of one per name.
#[derive(Default)]
struct SortedNames {
    text: String,
    starts: Vec<u32>,
}

impl SortedNames {
    /// Reads a file of one name per line, none if it does not exist.
    fn read(path: &Path) -> io::Result<Self> {
        let text = match fs::read_to_string(path) {
            Ok(text) => text,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(Self::default()),
            Err(error) => return Err(error),
        };
        if u32::try_from(text.len()).is_err() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "final list larger than 4 GB",
            ));
        }
        let mut starts = Vec::with_capacity(text.bytes().filter(|&byte| byte == b'\n').count() + 1);
        let mut start = 0;
        for line in text.split_inclusive('\n') {
            if !name_at(&text, start).is_empty() {
                starts.push(start);
            }
            start += line.len() as u32;
        }
        // Already sorted by the compilation, but a sorted list is what makes the search correct.
        starts
            .sort_unstable_by(|&first, &second| name_at(&text, first).cmp(name_at(&text, second)));
        Ok(Self { text, starts })
    }

    fn contains(&self, name: &str) -> bool {
        self.starts
            .binary_search_by(|&start| name_at(&self.text, start).cmp(name))
            .is_ok()
    }
}

/// The name of the line starting at `start`, without its line break.
fn name_at(text: &str, start: u32) -> &str {
    let rest = &text[start as usize..];
    let line = rest.split_once('\n').map_or(rest, |(line, _)| line);
    line.strip_suffix('\r').unwrap_or(line)
}

#[cfg(test)]
mod tests {
    use super::{BlockList, SortedNames, Verdict};
    use crate::rule;

    #[test]
    fn decides_from_the_final_list() {
        let directory = std::env::temp_dir().join(format!("adaway-filter-{}", std::process::id()));
        std::fs::create_dir_all(&directory).unwrap();
        rule::write_lines(
            &directory.join("blocked.txt"),
            ["ads.example.com", "tracker.net"],
        )
        .unwrap();
        rule::write_lines(&directory.join("redirected.txt"), ["nas.lan 192.168.1.10"]).unwrap();
        rule::write_lines(
            &directory.join("patterns.txt"),
            ["A good.ads.org", "B *.ads.org", "A spared.tracking.io"],
        )
        .unwrap();
        rule::write_lines(&directory.join("domains.txt"), ["tracking.io"]).unwrap();
        rule::write_lines(&directory.join("source_allowed.txt"), ["*.cdn.tracking.io"]).unwrap();

        let list = BlockList::load(&directory).unwrap();

        assert_eq!(list.verdict("ads.example.com"), Verdict::Block);
        assert_eq!(list.verdict("example.com"), Verdict::Allow);
        assert_eq!(
            list.verdict("nas.lan"),
            Verdict::Redirect("192.168.1.10".parse().unwrap())
        );
        assert_eq!(list.verdict("x.ads.org"), Verdict::Block);
        assert_eq!(list.verdict("good.ads.org"), Verdict::Allow);
        assert_eq!(list.verdict("tracking.io"), Verdict::Block);
        assert_eq!(list.verdict("pixel.eu.tracking.io"), Verdict::Block);
        assert_eq!(list.verdict("img.cdn.tracking.io"), Verdict::Allow);
        assert_eq!(list.verdict("spared.tracking.io"), Verdict::Allow);
        assert_eq!(list.verdict("nottracking.io"), Verdict::Allow);
        std::fs::remove_dir_all(&directory).unwrap();
        assert_eq!(
            BlockList::load(&directory)
                .unwrap()
                .verdict("ads.example.com"),
            Verdict::Allow
        );
    }

    #[test]
    fn finds_the_names_of_an_unsorted_file() {
        let directory = std::env::temp_dir().join(format!("adaway-names-{}", std::process::id()));
        std::fs::create_dir_all(&directory).unwrap();
        let path = directory.join("names.txt");
        std::fs::write(&path, "zeta.com\n\nalpha.org\r\nmid.net").unwrap();

        let names = SortedNames::read(&path).unwrap();

        assert!(names.contains("alpha.org"));
        assert!(names.contains("mid.net"));
        assert!(names.contains("zeta.com"));
        assert!(!names.contains(""));
        assert!(!names.contains("alpha"));
        std::fs::remove_dir_all(&directory).unwrap();
    }
}
