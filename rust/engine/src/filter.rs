//! The final list loaded in memory, answering for each queried name.

use std::collections::HashMap;
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
    blocked: Vec<Box<str>>,
    redirected: HashMap<Box<str>, IpAddr>,
    user_block_patterns: Vec<Pattern>,
    user_allowed: NameSet,
}

impl BlockList {
    /// Loads the final list written by [`crate::compile::compile`]. A list never computed is empty.
    pub fn load(final_directory: &Path) -> io::Result<Self> {
        let mut list = BlockList::default();
        for line in rule::read_lines(&final_directory.join("blocked.txt"))? {
            list.blocked.push(line.into_boxed_str());
        }
        // Already sorted by the compilation, but a sorted list is what makes the search correct.
        list.blocked.sort_unstable();
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
        let exact = self
            .blocked
            .binary_search_by(|host| (**host).cmp(name))
            .is_ok();
        exact
            || (self
                .user_block_patterns
                .iter()
                .any(|pattern| pattern.matches(name))
                && !self.user_allowed.matches(name))
    }
}

#[cfg(test)]
mod tests {
    use super::{BlockList, Verdict};
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
            ["A good.ads.org", "B *.ads.org"],
        )
        .unwrap();

        let list = BlockList::load(&directory).unwrap();

        assert_eq!(list.verdict("ads.example.com"), Verdict::Block);
        assert_eq!(list.verdict("example.com"), Verdict::Allow);
        assert_eq!(
            list.verdict("nas.lan"),
            Verdict::Redirect("192.168.1.10".parse().unwrap())
        );
        assert_eq!(list.verdict("x.ads.org"), Verdict::Block);
        assert_eq!(list.verdict("good.ads.org"), Verdict::Allow);
        std::fs::remove_dir_all(&directory).unwrap();
        assert_eq!(
            BlockList::load(&directory)
                .unwrap()
                .verdict("ads.example.com"),
            Verdict::Allow
        );
    }
}
