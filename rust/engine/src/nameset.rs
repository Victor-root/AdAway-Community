//! Sets of host names and wildcard patterns.

use std::collections::{HashMap, HashSet};

use crate::glob::{self, Pattern};

/// Exact names and patterns of one kind of rule.
///
/// Patterns are indexed by the dot-aligned end of their literal suffix (`ads.example.com` for
/// `*.ads.example.com`, `com` for `*example.com`), so a name is only tested against the patterns
/// indexed by one of its own dot-aligned ends.
#[derive(Default)]
pub struct NameSet {
    pub exact: HashSet<String>,
    patterns: HashMap<String, Vec<Pattern>>,
}

impl NameSet {
    pub fn insert(&mut self, name: String) {
        if glob::has_wildcards(&name) {
            let pattern = Pattern::new(name);
            let suffix = pattern.literal_suffix();
            let key = match suffix.strip_prefix('.') {
                Some(aligned) => aligned,
                None => suffix.split_once('.').map_or("", |(_, rest)| rest),
            };
            self.patterns
                .entry(key.to_owned())
                .or_default()
                .push(pattern);
        } else {
            self.exact.insert(name);
        }
    }

    pub fn matches(&self, name: &str) -> bool {
        if self.exact.contains(name) {
            return true;
        }
        let dot_aligned_ends = std::iter::once(name)
            .chain(name.match_indices('.').map(|(index, _)| &name[index + 1..]))
            .chain(std::iter::once(""));
        dot_aligned_ends
            .filter_map(|end| self.patterns.get(end))
            .flatten()
            .any(|pattern| pattern.matches(name))
    }

    pub fn patterns(&self) -> impl Iterator<Item = &Pattern> {
        self.patterns.values().flatten()
    }
}
