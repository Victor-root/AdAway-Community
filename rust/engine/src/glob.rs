//! Host name patterns: `*` matches any sequence of characters (dots included), `?` exactly one.

pub const WILDCARDS: [char; 2] = ['*', '?'];

pub fn has_wildcards(text: &str) -> bool {
    text.contains(WILDCARDS)
}

/// Returns whether `name` matches `pattern`. Both are expected in canonical (ASCII) form.
pub fn matches(pattern: &str, name: &str) -> bool {
    let pattern = pattern.as_bytes();
    let name = name.as_bytes();
    let (mut p, mut n) = (0, 0);
    // Position after the last `*` seen, and the name position it currently stands for.
    let mut backtrack: Option<(usize, usize)> = None;
    while n < name.len() {
        match pattern.get(p) {
            Some(b'*') => {
                p += 1;
                backtrack = Some((p, n));
            }
            Some(&c) if c == b'?' || c == name[n] => {
                p += 1;
                n += 1;
            }
            _ => match backtrack {
                Some((star_p, star_n)) => {
                    p = star_p;
                    n = star_n + 1;
                    backtrack = Some((star_p, star_n + 1));
                }
                None => return false,
            },
        }
    }
    pattern[p..].iter().all(|&c| c == b'*')
}

/// A pattern with a fast pre-check: the literal text after its last wildcard must end the name.
pub struct Pattern {
    text: String,
    literal_suffix_start: usize,
}

impl Pattern {
    pub fn new(text: String) -> Self {
        let literal_suffix_start = text.rfind(WILDCARDS).map_or(0, |index| index + 1);
        Self {
            text,
            literal_suffix_start,
        }
    }

    pub fn text(&self) -> &str {
        &self.text
    }

    /// The text after the last wildcard, which every matching name ends with.
    pub fn literal_suffix(&self) -> &str {
        &self.text[self.literal_suffix_start..]
    }

    pub fn matches(&self, name: &str) -> bool {
        name.ends_with(self.literal_suffix()) && matches(&self.text, name)
    }
}

#[cfg(test)]
mod tests {
    use super::{Pattern, matches};

    #[test]
    fn matches_wildcards() {
        assert!(matches("*.example.com", "ads.example.com"));
        assert!(matches("*.example.com", "a.b.example.com"));
        assert!(!matches("*.example.com", "example.com"));
        assert!(matches("*example.com", "example.com"));
        assert!(matches("*example.com", "badexample.com"));
        assert!(matches("ad?.example.com", "ads.example.com"));
        assert!(!matches("ad?.example.com", "ad.example.com"));
        assert!(matches("sub.*.example.com", "sub.cdn.example.com"));
        assert!(matches("*", "anything.org"));
        assert!(matches("a*b*c", "aXXbYYc"));
        assert!(!matches("a*b*c", "aXXbYY"));
    }

    #[test]
    fn treats_other_characters_literally() {
        assert!(matches("f_a.com", "f_a.com"));
        assert!(!matches("f_a.com", "fxa.com"));
        assert!(!matches("example.com", "example.org"));
    }

    #[test]
    fn checks_the_literal_suffix_first() {
        let pattern = Pattern::new("*.example.com".to_owned());
        assert!(pattern.matches("cdn.example.com"));
        assert!(!pattern.matches("cdn.example.org"));
        assert!(Pattern::new("example.*".to_owned()).matches("example.net"));
    }
}
