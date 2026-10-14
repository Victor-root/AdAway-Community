//! Paged search in the catalog files, for the list screens.

use std::fs::File;
use std::io::{self, BufRead, BufReader};
use std::path::Path;

use crate::glob;

/// Returns the lines of the catalog file at `path` whose host contains `filter` (where `*` and `?`
/// are wildcards), skipping the first `offset` matches. A missing file is an empty catalog.
pub fn query(path: &Path, filter: &str, offset: usize, limit: usize) -> io::Result<Vec<String>> {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(error) => return Err(error),
    };
    let filter = filter.trim().to_ascii_lowercase();
    let pattern = format!("*{filter}*");
    let mut results = Vec::new();
    let mut skipped = 0;
    for line in BufReader::new(file).lines() {
        let line = line?;
        let host = line.split(' ').next().unwrap_or_default();
        if !filter.is_empty() && !glob::matches(&pattern, host) {
            continue;
        }
        if skipped < offset {
            skipped += 1;
            continue;
        }
        results.push(line);
        if results.len() == limit {
            break;
        }
    }
    Ok(results)
}

#[cfg(test)]
mod tests {
    use super::query;
    use crate::rule;

    #[test]
    fn filters_and_pages() {
        let path = std::env::temp_dir().join(format!("adaway-catalog-{}.txt", std::process::id()));
        let lines = [
            "a.example.com",
            "ads.example.com",
            "b.example.org",
            "nas.lan 10.0.0.1",
        ];
        rule::write_lines(&path, lines).unwrap();

        assert_eq!(
            query(&path, "", 1, 2).unwrap(),
            ["ads.example.com", "b.example.org"]
        );
        assert_eq!(
            query(&path, " EXAMPLE.COM ", 0, 10).unwrap(),
            ["a.example.com", "ads.example.com"]
        );
        assert_eq!(query(&path, "a?s", 0, 10).unwrap(), ["ads.example.com"]);
        assert_eq!(query(&path, "*.org", 0, 10).unwrap(), ["b.example.org"]);
        assert_eq!(query(&path, "10.0", 0, 10).unwrap(), Vec::<String>::new());
        assert_eq!(query(&path, "nas", 0, 10).unwrap(), ["nas.lan 10.0.0.1"]);

        std::fs::remove_file(&path).unwrap();
        assert!(query(&path, "", 0, 10).unwrap().is_empty());
    }
}
