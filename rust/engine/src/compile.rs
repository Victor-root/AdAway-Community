//! Computation of the final list from the enabled sources and the user rules.
//!
//! The user rules always win over the sources. Within each of these two layers, a redirection wins
//! over an allow rule, which wins over a block rule. Block and redirect rules from sources match
//! exact names only, while allow rules and user block rules may use wildcards.
//!
//! Outputs, all sorted:
//! - `final/blocked.txt`: names to block,
//! - `final/redirected.txt`: `<host> <address>` redirections,
//! - `final/patterns.txt`: user rules that must also be checked at query time (`B <pattern>`
//!   block patterns, `A <pattern>` allow rules),
//! - `catalog/{blocked,allowed,redirected}.txt`: what the enabled sources contain, for display.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use crate::nameset::NameSet;
use crate::rule::{self, Rule};

/// Enabled user rules, in canonical form.
#[derive(Debug, Default)]
pub struct UserRules {
    pub blocked: Vec<String>,
    pub allowed: Vec<String>,
    pub redirected: Vec<(String, String)>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct Summary {
    /// Names blocked by an exact rule, plus user block patterns.
    pub blocked: u64,
    /// Distinct allow rules.
    pub allowed: u64,
    /// Redirected names.
    pub redirected: u64,
}

pub struct OutputDirectories<'a> {
    pub catalog: &'a Path,
    pub final_list: &'a Path,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum Decision {
    Block,
    Allow,
    Redirect(String),
}

/// One layer of rules: the user rules, or all the enabled sources together.
#[derive(Default)]
struct Layer {
    allowed: NameSet,
    redirected: HashMap<String, String>,
}

impl Layer {
    fn decide(&self, name: &str, blocked: bool) -> Option<Decision> {
        if let Some(address) = self.redirected.get(name) {
            Some(Decision::Redirect(address.clone()))
        } else if self.allowed.matches(name) {
            Some(Decision::Allow)
        } else if blocked {
            Some(Decision::Block)
        } else {
            None
        }
    }
}

/// Reads the source rule files (in priority order: the first redirection of a name wins) and
/// writes the final list and the catalog.
pub fn compile(
    source_files: &[PathBuf],
    user_rules: UserRules,
    outputs: OutputDirectories,
) -> io::Result<Summary> {
    let mut sources = Layer::default();
    let mut source_blocked: Vec<String> = Vec::new();
    let mut source_allowed: Vec<String> = Vec::new();
    for file in source_files {
        for rule in rule::read_rules(file)? {
            match rule {
                Rule::Blocked(host) => source_blocked.push(host),
                Rule::Allowed(pattern) => source_allowed.push(pattern),
                Rule::Redirected { host, address } => {
                    sources.redirected.entry(host).or_insert(address);
                }
            }
        }
    }
    source_blocked.sort_unstable();
    source_blocked.dedup();
    source_allowed.sort_unstable();
    source_allowed.dedup();
    source_allowed
        .iter()
        .cloned()
        .for_each(|pattern| sources.allowed.insert(pattern));

    let mut user = Layer::default();
    let mut user_blocked = NameSet::default();
    user_rules
        .blocked
        .iter()
        .cloned()
        .for_each(|host| user_blocked.insert(host));
    user_rules
        .allowed
        .iter()
        .cloned()
        .for_each(|pattern| user.allowed.insert(pattern));
    user.redirected
        .extend(user_rules.redirected.iter().cloned());

    let mut candidates: Vec<&str> = source_blocked
        .iter()
        .chain(&user_blocked.exact)
        .chain(sources.redirected.keys())
        .chain(user.redirected.keys())
        .map(String::as_str)
        .collect();
    candidates.sort_unstable();
    candidates.dedup();

    let mut blocked = Vec::new();
    let mut redirected = Vec::new();
    for name in candidates {
        let decision = user.decide(name, user_blocked.matches(name)).or_else(|| {
            sources.decide(
                name,
                source_blocked
                    .binary_search_by(|host| host.as_str().cmp(name))
                    .is_ok(),
            )
        });
        match decision {
            Some(Decision::Block) => blocked.push(name),
            Some(Decision::Redirect(address)) => redirected.push(format!("{name} {address}")),
            Some(Decision::Allow) | None => {}
        }
    }

    let mut patterns: Vec<String> = user_blocked
        .patterns()
        .map(|pattern| Rule::Blocked(pattern.text().to_owned()).to_line())
        .chain(
            user_rules
                .allowed
                .iter()
                .map(|pattern| Rule::Allowed(pattern.clone()).to_line()),
        )
        .collect();
    patterns.sort_unstable();

    let mut catalog_redirected: Vec<String> = sources
        .redirected
        .iter()
        .map(|(host, address)| format!("{host} {address}"))
        .collect();
    catalog_redirected.sort_unstable();

    let allowed_count = source_allowed
        .iter()
        .chain(&user_rules.allowed)
        .collect::<HashSet<_>>()
        .len();
    let summary = Summary {
        blocked: (blocked.len() + user_blocked.patterns().count()) as u64,
        allowed: allowed_count as u64,
        redirected: redirected.len() as u64,
    };

    fs::create_dir_all(outputs.final_list)?;
    fs::create_dir_all(outputs.catalog)?;
    rule::write_lines(&outputs.final_list.join("blocked.txt"), &blocked)?;
    rule::write_lines(&outputs.final_list.join("redirected.txt"), &redirected)?;
    rule::write_lines(&outputs.final_list.join("patterns.txt"), &patterns)?;
    rule::write_lines(&outputs.catalog.join("blocked.txt"), &source_blocked)?;
    rule::write_lines(&outputs.catalog.join("allowed.txt"), &source_allowed)?;
    rule::write_lines(&outputs.catalog.join("redirected.txt"), &catalog_redirected)?;
    Ok(summary)
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::PathBuf;

    use super::{OutputDirectories, Summary, UserRules, compile};
    use crate::rule::{self, Rule};

    struct Workspace(PathBuf);

    impl Workspace {
        fn new(name: &str) -> Self {
            let path =
                std::env::temp_dir().join(format!("adaway-compile-{name}-{}", std::process::id()));
            let _ = fs::remove_dir_all(&path);
            fs::create_dir_all(&path).unwrap();
            Self(path)
        }

        fn source(&self, name: &str, rules: &[Rule]) -> PathBuf {
            let path = self.0.join(name);
            rule::write_lines(&path, rules.iter().map(Rule::to_line)).unwrap();
            path
        }

        fn run(&self, sources: &[PathBuf], user: UserRules) -> Summary {
            let catalog = self.0.join("catalog");
            let final_list = self.0.join("final");
            compile(
                sources,
                user,
                OutputDirectories {
                    catalog: &catalog,
                    final_list: &final_list,
                },
            )
            .unwrap()
        }

        fn read(&self, file: &str) -> Vec<String> {
            fs::read_to_string(self.0.join(file))
                .unwrap()
                .lines()
                .map(str::to_owned)
                .collect()
        }
    }

    impl Drop for Workspace {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    fn blocked(host: &str) -> Rule {
        Rule::Blocked(host.to_owned())
    }

    fn allowed(pattern: &str) -> Rule {
        Rule::Allowed(pattern.to_owned())
    }

    fn redirected(host: &str, address: &str) -> Rule {
        Rule::Redirected {
            host: host.to_owned(),
            address: address.to_owned(),
        }
    }

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| (*value).to_owned()).collect()
    }

    #[test]
    fn merges_sources_and_applies_allow_rules() {
        let workspace = Workspace::new("merge");
        let first = workspace.source(
            "1",
            &[
                blocked("a.com"),
                blocked("b.com"),
                blocked("cdn.example.com"),
            ],
        );
        let second = workspace.source(
            "2",
            &[blocked("b.com"), blocked("c.com"), allowed("*.example.com")],
        );

        let summary = workspace.run(&[first, second], UserRules::default());

        assert_eq!(
            workspace.read("final/blocked.txt"),
            strings(&["a.com", "b.com", "c.com"])
        );
        assert_eq!(
            workspace.read("catalog/blocked.txt"),
            strings(&["a.com", "b.com", "c.com", "cdn.example.com"])
        );
        assert_eq!(
            workspace.read("catalog/allowed.txt"),
            strings(&["*.example.com"])
        );
        assert_eq!(
            summary,
            Summary {
                blocked: 3,
                allowed: 1,
                redirected: 0
            }
        );
    }

    #[test]
    fn gives_priority_to_the_user_rules() {
        let workspace = Workspace::new("priority");
        let source = workspace.source(
            "1",
            &[
                blocked("ads.com"),
                allowed("kept.com"),
                redirected("nas.lan", "10.0.0.1"),
                redirected("tv.lan", "10.0.0.2"),
            ],
        );
        let user = UserRules {
            blocked: strings(&["kept.com", "*.tracker.net"]),
            allowed: strings(&["ads.com", "tv.lan"]),
            redirected: vec![("nas.lan".to_owned(), "192.168.1.5".to_owned())],
        };

        let summary = workspace.run(&[source], user);

        assert_eq!(workspace.read("final/blocked.txt"), strings(&["kept.com"]));
        assert_eq!(
            workspace.read("final/redirected.txt"),
            strings(&["nas.lan 192.168.1.5"])
        );
        assert_eq!(
            workspace.read("final/patterns.txt"),
            strings(&["A ads.com", "A tv.lan", "B *.tracker.net"])
        );
        assert_eq!(
            workspace.read("catalog/redirected.txt"),
            strings(&["nas.lan 10.0.0.1", "tv.lan 10.0.0.2"])
        );
        assert_eq!(
            summary,
            Summary {
                blocked: 2,
                allowed: 3,
                redirected: 1
            }
        );
    }

    #[test]
    fn redirects_with_the_first_source_and_over_blocks() {
        let workspace = Workspace::new("redirects");
        let first = workspace.source("1", &[blocked("x.com"), redirected("y.com", "10.0.0.1")]);
        let second = workspace.source(
            "2",
            &[
                redirected("x.com", "10.0.0.2"),
                redirected("y.com", "10.0.0.3"),
            ],
        );

        workspace.run(&[first, second], UserRules::default());

        assert!(workspace.read("final/blocked.txt").is_empty());
        assert_eq!(
            workspace.read("final/redirected.txt"),
            strings(&["x.com 10.0.0.2", "y.com 10.0.0.1"])
        );
    }

    #[test]
    fn user_block_patterns_override_source_rules() {
        let workspace = Workspace::new("patterns");
        let source = workspace.source(
            "1",
            &[allowed("a.ads.net"), redirected("b.ads.net", "10.0.0.1")],
        );
        let user = UserRules {
            blocked: strings(&["*.ads.net"]),
            ..UserRules::default()
        };

        workspace.run(&[source], user);

        assert_eq!(workspace.read("final/blocked.txt"), strings(&["b.ads.net"]));
        assert!(workspace.read("final/redirected.txt").is_empty());
    }
}
