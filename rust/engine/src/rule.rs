//! Rules and the line format of the engine files: `B <host>`, `A <pattern>`, `R <host> <address>`.

use std::fs::{self, File};
use std::io::{self, BufRead, BufReader, BufWriter, Write};
use std::path::Path;

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Rule {
    Blocked(String),
    Allowed(String),
    Redirected { host: String, address: String },
}

impl Rule {
    pub fn to_line(&self) -> String {
        match self {
            Rule::Blocked(host) => format!("B {host}"),
            Rule::Allowed(pattern) => format!("A {pattern}"),
            Rule::Redirected { host, address } => format!("R {host} {address}"),
        }
    }

    pub fn from_line(line: &str) -> Option<Rule> {
        let mut parts = line.split(' ');
        let rule = match (parts.next()?, parts.next()?, parts.next()) {
            ("B", host, None) => Rule::Blocked(host.to_owned()),
            ("A", pattern, None) => Rule::Allowed(pattern.to_owned()),
            ("R", host, Some(address)) => Rule::Redirected {
                host: host.to_owned(),
                address: address.to_owned(),
            },
            _ => return None,
        };
        parts.next().is_none().then_some(rule)
    }
}

/// Reads a rule file, skipping lines that are not rules.
pub fn read_rules(path: &Path) -> io::Result<Vec<Rule>> {
    let reader = BufReader::new(File::open(path)?);
    let mut rules = Vec::new();
    for line in reader.lines() {
        if let Some(rule) = Rule::from_line(&line?) {
            rules.push(rule);
        }
    }
    Ok(rules)
}

/// Reads the lines of a file, none if it does not exist.
pub fn read_lines(path: &Path) -> io::Result<Vec<String>> {
    match File::open(path) {
        Ok(file) => BufReader::new(file).lines().collect(),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Vec::new()),
        Err(error) => Err(error),
    }
}

/// Writes `lines` to `path` through a temporary file, so that readers never see a partial file.
pub fn write_lines<I, S>(path: &Path, lines: I) -> io::Result<()>
where
    I: IntoIterator<Item = S>,
    S: AsRef<str>,
{
    let temporary = path.with_extension("tmp");
    {
        let mut writer = BufWriter::new(File::create(&temporary)?);
        for line in lines {
            writer.write_all(line.as_ref().as_bytes())?;
            writer.write_all(b"\n")?;
        }
        writer
            .into_inner()
            .map_err(io::IntoInnerError::into_error)?
            .sync_all()?;
    }
    fs::rename(&temporary, path)
}

#[cfg(test)]
mod tests {
    use super::Rule;

    #[test]
    fn round_trips_lines() {
        let rules = [
            Rule::Blocked("ads.example.com".to_owned()),
            Rule::Allowed("*.example.org".to_owned()),
            Rule::Redirected {
                host: "nas.lan".to_owned(),
                address: "192.168.1.10".to_owned(),
            },
        ];
        for rule in rules {
            assert_eq!(Rule::from_line(&rule.to_line()), Some(rule));
        }
    }

    #[test]
    fn rejects_malformed_lines() {
        for line in ["", "B", "X host", "B a b", "R host", "R a b c"] {
            assert_eq!(Rule::from_line(line), None, "{line}");
        }
    }
}
