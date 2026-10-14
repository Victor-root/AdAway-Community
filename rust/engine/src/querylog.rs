//! The names apps asked for while the user records them, for the DNS log screen. Each name is kept
//! once, in the order first asked, the oldest forgotten past [`MAX_NAMES`].

use std::collections::{HashSet, VecDeque};
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

const MAX_NAMES: usize = 8192;

#[derive(Default)]
pub struct QueryLog {
    recording: AtomicBool,
    names: Mutex<Names>,
}

#[derive(Default)]
struct Names {
    order: VecDeque<Box<str>>,
    known: HashSet<Box<str>>,
}

impl QueryLog {
    pub fn is_recording(&self) -> bool {
        self.recording.load(Ordering::Relaxed)
    }

    pub fn set_recording(&self, recording: bool) {
        self.recording.store(recording, Ordering::Relaxed);
    }

    /// Notes a queried name, when recording. Nothing is locked otherwise.
    pub fn record(&self, name: &str) {
        if !self.is_recording() || name.is_empty() {
            return;
        }
        let Ok(mut names) = self.names.lock() else {
            return;
        };
        if names.known.contains(name) {
            return;
        }
        if names.order.len() >= MAX_NAMES
            && let Some(oldest) = names.order.pop_front()
        {
            names.known.remove(&oldest);
        }
        names.order.push_back(name.into());
        names.known.insert(name.into());
    }

    pub fn names(&self) -> Vec<String> {
        self.names
            .lock()
            .map(|names| names.order.iter().map(|name| name.to_string()).collect())
            .unwrap_or_default()
    }

    pub fn clear(&self) {
        if let Ok(mut names) = self.names.lock() {
            names.order.clear();
            names.known.clear();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{MAX_NAMES, QueryLog};

    #[test]
    fn records_each_name_once_while_recording() {
        let log = QueryLog::default();
        log.record("ignored.example.com");
        log.set_recording(true);
        log.record("a.example.com");
        log.record("b.example.com");
        log.record("a.example.com");
        assert_eq!(log.names(), ["a.example.com", "b.example.com"]);
        log.clear();
        assert!(log.names().is_empty());
    }

    #[test]
    fn forgets_the_oldest_names() {
        let log = QueryLog::default();
        log.set_recording(true);
        for index in 0..=MAX_NAMES {
            log.record(&format!("host{index}.example.com"));
        }
        let names = log.names();
        assert_eq!(names.len(), MAX_NAMES);
        assert_eq!(names[0], "host1.example.com");
    }
}
