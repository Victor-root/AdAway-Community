//! AdAway blocking engine: list rules, final list computation and DNS filtering.

pub mod address;
pub mod catalog;
pub mod compile;
pub mod dns;
pub mod filter;
pub mod glob;
pub mod hostname;
pub mod nameset;
pub mod packet;
pub mod rule;
pub mod source;
pub mod tunnel;
