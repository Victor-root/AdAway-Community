//! Reading of the lists published by sources: hosts files, one domain per line, simple Adblock
//! rules (`||domain^`) and allow lists.

use std::collections::HashSet;
use std::fs::File;
use std::io::{self, BufRead, BufReader};
use std::net::IpAddr;
use std::path::Path;

use crate::address;
use crate::hostname;
use crate::rule::{self, Rule};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SourceFormat {
    /// Hosts to block, and redirections when enabled.
    Block,
    /// Hosts or patterns to allow.
    Allow,
}

#[derive(Clone, Copy, Debug)]
pub struct ParseOptions {
    pub format: SourceFormat,
    pub redirects: bool,
    pub max_rules: usize,
}

#[derive(Debug)]
pub enum ParseError {
    Io(io::Error),
    TooManyRules,
    NoRules,
}

impl From<io::Error> for ParseError {
    fn from(error: io::Error) -> Self {
        ParseError::Io(error)
    }
}

/// Names that hosts files map to the device itself, never worth blocking.
const LOCAL_NAMES: [&str; 11] = [
    "localhost",
    "localhost.localdomain",
    "local",
    "broadcasthost",
    "ip6-localhost",
    "ip6-loopback",
    "ip6-localnet",
    "ip6-mcastprefix",
    "ip6-allnodes",
    "ip6-allrouters",
    "ip6-allhosts",
];

/// Converts the downloaded list at `input` into a rule file at `output`, returning the number of
/// rules. `output` is only replaced once the whole list has been read successfully.
pub fn convert_file(
    input: &Path,
    output: &Path,
    options: ParseOptions,
) -> Result<usize, ParseError> {
    let rules = parse(BufReader::new(File::open(input)?), options)?;
    rule::write_lines(output, rules.iter().map(Rule::to_line))?;
    Ok(rules.len())
}

/// Parses a list, returning its distinct rules sorted by kind then name.
pub fn parse<R: BufRead>(mut reader: R, options: ParseOptions) -> Result<Vec<Rule>, ParseError> {
    let mut collector = Collector::default();
    let mut buffer = Vec::new();
    let mut first_line = true;
    while reader.read_until(b'\n', &mut buffer)? > 0 {
        let text = String::from_utf8_lossy(&buffer);
        let line = if first_line {
            text.trim_start_matches('\u{feff}')
        } else {
            &text
        };
        first_line = false;
        parse_line(line, options, &mut collector);
        if collector.len() > options.max_rules {
            return Err(ParseError::TooManyRules);
        }
        buffer.clear();
    }
    if collector.len() == 0 {
        return Err(ParseError::NoRules);
    }
    Ok(collector.into_rules())
}

fn parse_line(line: &str, options: ParseOptions, collector: &mut Collector) {
    if is_element_hiding(line) {
        return;
    }
    let line = line.split('#').next().unwrap_or_default().trim();
    if line.is_empty() || line.starts_with('!') || line.starts_with('[') {
        return;
    }
    match options.format {
        SourceFormat::Block => parse_block_line(line, options.redirects, collector),
        SourceFormat::Allow => parse_allow_line(line, collector),
    }
}

/// An element hiding rule of Adblock lists, as `example.com##.banner`: it hides part of a page and
/// blocks no host.
fn is_element_hiding(line: &str) -> bool {
    line.find('#').is_some_and(|start| {
        start > 0
            && !line[..start].ends_with(char::is_whitespace)
            && line[start + 1..]
                .trim_start_matches(['@', '?', '$', '%'])
                .starts_with('#')
    })
}

fn parse_block_line(line: &str, redirects: bool, collector: &mut Collector) {
    if let Some(domain) = adblock_domain(line) {
        if let Some(host) = block_host(domain) {
            collector.block(host);
        }
        return;
    }
    let mut tokens = line.split_whitespace();
    let Some(first) = tokens.next() else { return };
    let mut hosts = tokens.peekable();
    if hosts.peek().is_none() {
        if let Some(host) = block_host(first) {
            collector.block(host);
        }
        return;
    }
    if is_blocking_address(first) {
        hosts
            .filter_map(block_host)
            .for_each(|host| collector.block(host));
    } else if redirects && let Some(address) = address::normalize_ip(first) {
        for host in hosts.filter_map(block_host) {
            collector.redirect(host, address.clone());
        }
    }
}

fn parse_allow_line(line: &str, collector: &mut Collector) {
    let line = line.strip_prefix("@@").unwrap_or(line);
    if let Some(domain) = adblock_domain(line) {
        if let Some(pattern) = hostname::normalize(domain, true) {
            collector.allow(pattern);
        }
        return;
    }
    let tokens: Vec<&str> = line.split_whitespace().collect();
    let patterns = match tokens.as_slice() {
        [_] => &tokens[..],
        [address, hosts @ ..] if address.parse::<IpAddr>().is_ok() => hosts,
        _ => return,
    };
    for pattern in patterns
        .iter()
        .filter_map(|pattern| hostname::normalize(pattern, true))
    {
        collector.allow(pattern);
    }
}

/// The domain of a plain `||domain^` Adblock rule. Rules with options or paths are not DNS rules.
fn adblock_domain(line: &str) -> Option<&str> {
    line.strip_prefix("||")?.strip_suffix('^')
}

fn block_host(token: &str) -> Option<String> {
    hostname::normalize(token, false).filter(|host| !LOCAL_NAMES.contains(&host.as_str()))
}

/// Whether a hosts file address sends the name nowhere: `::`, the loopback range, the `0.0.0.0/8`
/// range (`0.0.0.0`, but also `0.0.0.1` for some lists), or the short `0` some lists use.
fn is_blocking_address(token: &str) -> bool {
    token == "0"
        || token.parse::<IpAddr>().is_ok_and(|address| match address {
            IpAddr::V4(address) => address.octets()[0] == 0 || address.is_loopback(),
            IpAddr::V6(address) => address.is_unspecified() || address.is_loopback(),
        })
}

/// Gathers the rules of a list. Duplicates are only removed at the end, sorting being much
/// faster than hashing every name of a large list.
#[derive(Default)]
struct Collector {
    blocked: Vec<String>,
    allowed: Vec<String>,
    redirected: Vec<(String, String)>,
    redirected_hosts: HashSet<String>,
}

impl Collector {
    fn len(&self) -> usize {
        self.blocked.len() + self.allowed.len() + self.redirected.len()
    }

    fn block(&mut self, host: String) {
        self.blocked.push(host);
    }

    fn allow(&mut self, pattern: String) {
        self.allowed.push(pattern);
    }

    /// The first redirection of a host wins.
    fn redirect(&mut self, host: String, address: String) {
        if self.redirected_hosts.insert(host.clone()) {
            self.redirected.push((host, address));
        }
    }

    fn into_rules(self) -> Vec<Rule> {
        let Collector {
            mut blocked,
            mut allowed,
            mut redirected,
            ..
        } = self;
        blocked.sort_unstable();
        blocked.dedup();
        allowed.sort_unstable();
        allowed.dedup();
        redirected.sort_unstable();
        blocked
            .into_iter()
            .map(Rule::Blocked)
            .chain(allowed.into_iter().map(Rule::Allowed))
            .chain(
                redirected
                    .into_iter()
                    .map(|(host, address)| Rule::Redirected { host, address }),
            )
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::{ParseError, ParseOptions, SourceFormat, parse};
    use crate::rule::Rule;

    const BLOCK: ParseOptions = ParseOptions {
        format: SourceFormat::Block,
        redirects: false,
        max_rules: 100,
    };

    fn parse_text(text: &str, options: ParseOptions) -> Result<Vec<Rule>, ParseError> {
        parse(text.as_bytes(), options)
    }

    fn blocked(text: &str) -> Vec<String> {
        parse_text(text, BLOCK)
            .unwrap()
            .into_iter()
            .map(|rule| match rule {
                Rule::Blocked(host) => host,
                other => panic!("unexpected {other:?}"),
            })
            .collect()
    }

    #[test]
    fn reads_hosts_files() {
        let text = "\u{feff}# Title\n127.0.0.1 localhost\n::1 ip6-localhost\n0.0.0.0 Ads.Example.COM # ad\n\
                    127.0.0.1\ta.com b.com\n:: c.com\n127.0.0.2 d.com\n0 e.com\n0.0.0.1 f.com\n0.0.0.0 0.0.0.0\r\n";
        assert_eq!(
            blocked(text),
            [
                "a.com",
                "ads.example.com",
                "b.com",
                "c.com",
                "d.com",
                "e.com",
                "f.com"
            ]
        );
    }

    #[test]
    fn reads_domain_lists_and_adblock_rules() {
        let text = "! Adblock comment\n[Adblock Plus 2.0]\nads.example.com\n||tracker.example.net^\n\
                    ||options.example.org^$third-party\n||path.example.org/ad^\n\
                    news.example.org##.banner\nshop.example.org#@#.promo\nmail.example.org#?#.ad\n";
        assert_eq!(blocked(text), ["ads.example.com", "tracker.example.net"]);
    }

    #[test]
    fn skips_invalid_entries() {
        let text = "0.0.0.0 *.example.com\n0.0.0.0 bad_.com\nnot a valid line\n10.0.0.1 nas.lan\n";
        assert!(matches!(parse_text(text, BLOCK), Err(ParseError::NoRules)));
    }

    #[test]
    fn reads_redirections_when_enabled() {
        let options = ParseOptions {
            redirects: true,
            ..BLOCK
        };
        let rules = parse_text(
            "192.168.1.10 nas.lan\n10.0.0.1 nas.lan\n2001:DB8::1 v6.lan\n",
            options,
        )
        .unwrap();
        assert_eq!(
            rules,
            [
                Rule::Redirected {
                    host: "nas.lan".to_owned(),
                    address: "192.168.1.10".to_owned()
                },
                Rule::Redirected {
                    host: "v6.lan".to_owned(),
                    address: "2001:db8::1".to_owned()
                },
            ]
        );
    }

    #[test]
    fn reads_allow_lists() {
        let options = ParseOptions {
            format: SourceFormat::Allow,
            ..BLOCK
        };
        let rules = parse_text(
            "*.example.com # cdn\n0.0.0.0 safe.org\n@@||good.net^\nbad host\n",
            options,
        )
        .unwrap();
        assert_eq!(
            rules,
            [
                Rule::Allowed("*.example.com".to_owned()),
                Rule::Allowed("good.net".to_owned()),
                Rule::Allowed("safe.org".to_owned()),
            ]
        );
    }

    #[test]
    fn enforces_the_rule_limit() {
        let options = ParseOptions {
            max_rules: 2,
            ..BLOCK
        };
        let result = parse_text("a.com\nb.com\nc.com\n", options);
        assert!(matches!(result, Err(ParseError::TooManyRules)));
    }
}
