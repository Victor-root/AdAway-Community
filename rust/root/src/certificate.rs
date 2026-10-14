//! The certificate of the HTTPS server, generated on each device so that its key is never shared.
//!
//! It is self-signed and marked as a CA so that Android accepts to install it, but its key usage
//! does not allow it to sign other certificates: once installed, it only vouches for itself.

use std::fmt;
use std::fs;
use std::io;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::path::Path;

use rcgen::{
    BasicConstraints, CertificateParams, DistinguishedName, DnType, ExtendedKeyUsagePurpose, IsCa,
    KeyPair, KeyUsagePurpose, SanType,
};
use time::{Duration, OffsetDateTime};

/// The names served, as the 6.x certificate covered them.
const NAMES: [&str; 3] = ["localhost", "*.doubleclick.net", "*.g.doubleclick.net"];
const VALIDITY_DAYS: i64 = 3650;

#[derive(Debug)]
pub enum Error {
    Generation(rcgen::Error),
    Io(io::Error),
}

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Generation(error) => write!(formatter, "certificate generation failed: {error}"),
            Error::Io(error) => write!(formatter, "certificate not written: {error}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<rcgen::Error> for Error {
    fn from(error: rcgen::Error) -> Self {
        Error::Generation(error)
    }
}

impl From<io::Error> for Error {
    fn from(error: io::Error) -> Self {
        Error::Io(error)
    }
}

/// Writes a new certificate and its private key, both in PEM.
pub fn generate(certificate: &Path, key: &Path) -> Result<(), Error> {
    let mut params = CertificateParams::new(NAMES.map(String::from).to_vec())?;
    params.subject_alt_names.extend([
        SanType::IpAddress(IpAddr::V4(Ipv4Addr::LOCALHOST)),
        SanType::IpAddress(IpAddr::V6(Ipv6Addr::LOCALHOST)),
    ]);
    let mut name = DistinguishedName::new();
    name.push(DnType::CommonName, "localhost");
    params.distinguished_name = name;
    params.is_ca = IsCa::Ca(BasicConstraints::Unconstrained);
    params.key_usages = vec![KeyUsagePurpose::DigitalSignature];
    params.extended_key_usages = vec![ExtendedKeyUsagePurpose::ServerAuth];
    let now = OffsetDateTime::now_utc();
    params.not_before = now - Duration::days(1);
    params.not_after = now + Duration::days(VALIDITY_DAYS);
    let key_pair = KeyPair::generate()?;
    let generated = params.self_signed(&key_pair)?;
    fs::write(key, key_pair.serialize_pem())?;
    fs::write(certificate, generated.pem())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::generate;
    use std::fs;

    #[test]
    fn writes_a_certificate_and_its_key() {
        let directory =
            std::env::temp_dir().join(format!("adaway-certificate-{}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        let (certificate, key) = (directory.join("cert.pem"), directory.join("key.pem"));
        generate(&certificate, &key).unwrap();
        assert!(
            fs::read_to_string(&certificate)
                .unwrap()
                .starts_with("-----BEGIN CERTIFICATE-----")
        );
        assert!(
            fs::read_to_string(&key)
                .unwrap()
                .starts_with("-----BEGIN PRIVATE KEY-----")
        );
        fs::remove_dir_all(&directory).unwrap();
    }
}
