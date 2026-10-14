//! Host name rules shared by list parsing, user input and backups.

use crate::glob::WILDCARDS;

const MAX_NAME_LENGTH: usize = 253;
const MAX_LABEL_LENGTH: usize = 63;

/// Returns the canonical form of `input` (trimmed, lowercase, without the final root dot), or
/// `None` if it is not a valid host name.
///
/// With `allow_wildcards`, `*` (any sequence) and `?` (any single character) are accepted as long
/// as the pattern can match a valid host name. Only ASCII names are accepted: internationalized
/// names must be given in their punycode form.
pub fn normalize(input: &str, allow_wildcards: bool) -> Option<String> {
    let trimmed = input.trim();
    if !trimmed.is_ascii() {
        return None;
    }
    let mut name = trimmed.to_ascii_lowercase();
    if name.ends_with('.') {
        name.pop();
    }
    let valid = if name.contains(WILDCARDS) {
        allow_wildcards
            && (is_valid_name(&name.replace(WILDCARDS, "a"))
                || is_valid_name(&name.replace(WILDCARDS, "")))
    } else {
        is_valid_name(&name)
    };
    valid.then_some(name)
}

fn is_valid_name(name: &str) -> bool {
    if name.is_empty() || name.len() > MAX_NAME_LENGTH {
        return false;
    }
    let mut labels = name.split('.').peekable();
    while let Some(label) = labels.next() {
        if !is_valid_label(label) {
            return false;
        }
        let is_top_level = labels.peek().is_none();
        if is_top_level && label.starts_with(|c: char| c.is_ascii_digit()) {
            return false;
        }
    }
    true
}

fn is_valid_label(label: &str) -> bool {
    let is_separator = |c: char| c == '-' || c == '_';
    !label.is_empty()
        && label.len() <= MAX_LABEL_LENGTH
        && label
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || is_separator(c))
        && !label.starts_with(is_separator)
        && !label.ends_with(is_separator)
}

#[cfg(test)]
mod tests {
    use super::normalize;

    fn valid(input: &str) -> bool {
        normalize(input, false).is_some()
    }

    fn valid_pattern(input: &str) -> bool {
        normalize(input, true).is_some()
    }

    #[test]
    fn accepts_valid_names() {
        for name in [
            "foo.com",
            "f-_-o.com",
            "f--1.com",
            "f11-1.com",
            "www",
            "abc.a23",
            "biz.com.ua",
            "x",
            "f--o",
            "f_a",
        ] {
            assert!(valid(name), "{name} should be valid");
        }
    }

    #[test]
    fn rejects_invalid_names() {
        let long_label = format!("{}.com", "a".repeat(64));
        let too_many_labels = vec!["a"; 128].join(".");
        let too_long = format!("{}.com", vec!["abcdefghi"; 26].join("."));
        for name in [
            "",
            " ",
            "127.0.0.1",
            "::1",
            "13",
            "abc.12c",
            "foo-.com",
            "_bar.quux",
            "foo+bar.com",
            "foo!bar.com",
            ".foo.com",
            "..bar.com",
            "baz..com",
            "fleeb.com..",
            ".",
            "..",
            "...",
            "foo bar.com",
            "exämple.com",
            &long_label,
            &too_many_labels,
            &too_long,
        ] {
            assert!(!valid(name), "{name} should be invalid");
        }
    }

    #[test]
    fn normalizes_case_spaces_and_root_dot() {
        assert_eq!(
            normalize("  Ads.Example.COM.\t", false).as_deref(),
            Some("ads.example.com")
        );
    }

    #[test]
    fn rejects_wildcards_unless_allowed() {
        assert!(!valid("*.example.com"));
        assert!(valid_pattern("*.example.com"));
    }

    #[test]
    fn accepts_valid_patterns() {
        for pattern in [
            "*.example.com",
            "?.example.com",
            "*-example.com",
            "*_example.com",
            "example.*",
            "example.?",
            "example-*",
            "example_?",
            "sub.*.example.com",
            "f--?.com",
            "*",
            "?",
        ] {
            assert!(valid_pattern(pattern), "{pattern} should be valid");
        }
    }

    #[test]
    fn rejects_invalid_patterns() {
        for pattern in [
            "abc.12*",
            "foo*-.com",
            "_*bar.quux",
            "foo?+*bar.com",
            ".*foo.com",
            "..*bar.com",
            "*..bar.com",
            "baz*..com",
            "fleeb.com*..",
            "fleeb.com..*",
            "abc.12?",
            "foo?-.com",
        ] {
            assert!(!valid_pattern(pattern), "{pattern} should be invalid");
        }
    }
}
