//! JNI bindings exposing the engine to the Android app (`org.adaway.engine.Engine`).

use std::fmt;
use std::io;
use std::net::IpAddr;
use std::os::fd::{FromRawFd, OwnedFd};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, RwLock};

use adaway_engine::compile::{self, OutputDirectories, UserRules};
use adaway_engine::filter::BlockList;
use adaway_engine::source::{self, ParseError, ParseOptions, SourceFormat};
use adaway_engine::tunnel::{Exit, SharedRules, Stopper, Tunnel, TunnelConfig, Upstream};
use adaway_engine::{address, catalog, hostname};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong};
use jni::{Env, EnvUnowned};

/// Error codes returned by `parseSource`, mirrored in the Kotlin `Engine`.
const PARSE_IO_ERROR: jlong = -1;
const PARSE_TOO_MANY_RULES: jlong = -2;
const PARSE_NO_RULES: jlong = -3;

#[derive(Debug)]
enum BridgeError {
    Jni(jni::errors::Error),
    Io(io::Error),
    InvalidArgument(&'static str),
}

impl fmt::Display for BridgeError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BridgeError::Jni(error) => write!(formatter, "JNI error: {error}"),
            BridgeError::Io(error) => write!(formatter, "I/O error: {error}"),
            BridgeError::InvalidArgument(reason) => write!(formatter, "invalid argument: {reason}"),
        }
    }
}

impl std::error::Error for BridgeError {}

impl From<jni::errors::Error> for BridgeError {
    fn from(error: jni::errors::Error) -> Self {
        BridgeError::Jni(error)
    }
}

impl From<io::Error> for BridgeError {
    fn from(error: io::Error) -> Self {
        BridgeError::Io(error)
    }
}

fn optional_string<'local>(
    env: &mut Env<'local>,
    value: Option<String>,
) -> jni::errors::Result<JString<'local>> {
    match value {
        Some(value) => JString::new(env, value),
        None => Ok(JString::null()),
    }
}

fn read_strings(
    env: &mut Env<'_>,
    array: &JObjectArray<'_, JString<'_>>,
) -> jni::errors::Result<Vec<String>> {
    let length = array.len(env)?;
    let mut strings = Vec::with_capacity(length);
    for index in 0..length {
        let element = array.get_element(env, index)?;
        strings.push(element.try_to_string(env)?);
        env.delete_local_ref(element);
    }
    Ok(strings)
}

fn new_string_array<'local>(
    env: &mut Env<'local>,
    strings: &[String],
) -> jni::errors::Result<JObjectArray<'local, JString<'local>>> {
    let array = JObjectArray::<JString>::new(env, strings.len(), JString::null())?;
    for (index, string) in strings.iter().enumerate() {
        let element = JString::new(env, string)?;
        array.set_element(env, index, &element)?;
        env.delete_local_ref(element);
    }
    Ok(array)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_normalizeHostname<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    host: JString<'caller>,
    allow_wildcards: jboolean,
) -> JString<'caller> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<_> {
            let host = host.try_to_string(env)?;
            optional_string(env, hostname::normalize(&host, allow_wildcards))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_normalizeIpAddress<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    address: JString<'caller>,
) -> JString<'caller> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<_> {
            let address = address.try_to_string(env)?;
            optional_string(env, address::normalize_ip(&address))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_parseSource<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    input: JString<'caller>,
    output: JString<'caller>,
    allow_format: jboolean,
    redirects: jboolean,
    max_rules: jint,
) -> jlong {
    unowned_env
        .with_env(|env| -> jni::errors::Result<_> {
            let input = input.try_to_string(env)?;
            let output = output.try_to_string(env)?;
            let options = ParseOptions {
                format: if allow_format {
                    SourceFormat::Allow
                } else {
                    SourceFormat::Block
                },
                redirects,
                max_rules: usize::try_from(max_rules).unwrap_or(0),
            };
            Ok(
                match source::convert_file(Path::new(&input), Path::new(&output), options) {
                    Ok(count) => jlong::try_from(count).unwrap_or(jlong::MAX),
                    Err(ParseError::Io(_)) => PARSE_IO_ERROR,
                    Err(ParseError::TooManyRules) => PARSE_TOO_MANY_RULES,
                    Err(ParseError::NoRules) => PARSE_NO_RULES,
                },
            )
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_org_adaway_engine_Engine_compile<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    source_files: JObjectArray<'caller, JString<'caller>>,
    user_blocked: JObjectArray<'caller, JString<'caller>>,
    user_allowed: JObjectArray<'caller, JString<'caller>>,
    user_redirect_hosts: JObjectArray<'caller, JString<'caller>>,
    user_redirect_addresses: JObjectArray<'caller, JString<'caller>>,
    catalog_directory: JString<'caller>,
    final_directory: JString<'caller>,
) -> JLongArray<'caller> {
    unowned_env
        .with_env(|env| -> Result<_, BridgeError> {
            let source_files: Vec<PathBuf> = read_strings(env, &source_files)?
                .into_iter()
                .map(PathBuf::from)
                .collect();
            let user_rules = UserRules {
                blocked: read_strings(env, &user_blocked)?,
                allowed: read_strings(env, &user_allowed)?,
                redirected: read_strings(env, &user_redirect_hosts)?
                    .into_iter()
                    .zip(read_strings(env, &user_redirect_addresses)?)
                    .collect(),
            };
            let catalog = PathBuf::from(catalog_directory.try_to_string(env)?);
            let final_list = PathBuf::from(final_directory.try_to_string(env)?);
            let summary = compile::compile(
                &source_files,
                user_rules,
                OutputDirectories {
                    catalog: &catalog,
                    final_list: &final_list,
                },
            )?;
            let counts = [summary.blocked, summary.allowed, summary.redirected]
                .map(|count| jlong::try_from(count).unwrap_or(jlong::MAX));
            let array = JLongArray::new(env, counts.len())?;
            array.set_region(env, 0, &counts)?;
            Ok(array)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_queryCatalog<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    path: JString<'caller>,
    filter: JString<'caller>,
    offset: jint,
    limit: jint,
) -> JObjectArray<'caller, JString<'caller>> {
    unowned_env
        .with_env(|env| -> Result<_, BridgeError> {
            let path = PathBuf::from(path.try_to_string(env)?);
            let filter = filter.try_to_string(env)?;
            let lines = catalog::query(
                &path,
                &filter,
                usize::try_from(offset).unwrap_or(0),
                usize::try_from(limit).unwrap_or(0),
            )?;
            Ok(new_string_array(env, &lines)?)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// A tunnel shared between the thread running it and the threads stopping or reloading it.
struct TunnelHandle {
    tunnel: Mutex<Option<Tunnel>>,
    stopper: Stopper,
    rules: SharedRules,
    final_directory: PathBuf,
}

/// Exit codes of `runTunnel`, mirrored in the Kotlin `Engine`.
fn exit_code(exit: Exit) -> jint {
    match exit {
        Exit::Stopped => 0,
        Exit::TunnelClosed => 1,
        Exit::NetworkUnreachable => 2,
    }
}

/// Recovers a handle created by `createTunnel` and not destroyed yet, as the Kotlin side guarantees.
///
/// # Safety
///
/// `handle` must come from `createTunnel` and `destroyTunnel` must not have been called on it.
unsafe fn tunnel_handle<'a>(handle: jlong) -> &'a TunnelHandle {
    // SAFETY: guaranteed by the caller.
    unsafe { &*(handle as *const TunnelHandle) }
}

/// Takes ownership of the tun and socket descriptors, which are closed when the tunnel ends, and
/// returns a handle for the other tunnel functions.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_createTunnel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    tun_fd: jint,
    virtual_addresses: JObjectArray<'caller, JString<'caller>>,
    upstream_fds: JIntArray<'caller>,
    final_directory: JString<'caller>,
    monitor: jboolean,
) -> jlong {
    // Owned right away, so that every descriptor is closed if anything below fails.
    // SAFETY: the Kotlin side detached this descriptor and hands its ownership over.
    let tun = unsafe { OwnedFd::from_raw_fd(tun_fd) };
    unowned_env
        .with_env(|env| -> Result<_, BridgeError> {
            let length = upstream_fds.len(env)?;
            let mut fds = vec![0; length];
            upstream_fds.get_region(env, 0, &mut fds)?;
            // SAFETY: as for the tun descriptor.
            let sockets: Vec<OwnedFd> = fds
                .into_iter()
                .map(|fd| unsafe { OwnedFd::from_raw_fd(fd) })
                .collect();
            let addresses = read_strings(env, &virtual_addresses)?;
            if addresses.len() != sockets.len() {
                return Err(BridgeError::InvalidArgument(
                    "one virtual address per upstream socket",
                ));
            }
            let upstreams = addresses
                .iter()
                .zip(sockets)
                .map(|(address, socket)| {
                    let virtual_address = address
                        .parse::<IpAddr>()
                        .map_err(|_| BridgeError::InvalidArgument("virtual address"))?;
                    Ok(Upstream {
                        virtual_address,
                        socket,
                    })
                })
                .collect::<Result<Vec<_>, BridgeError>>()?;
            let final_directory = PathBuf::from(final_directory.try_to_string(env)?);
            let rules: SharedRules =
                Arc::new(RwLock::new(Arc::new(BlockList::load(&final_directory)?)));
            let tunnel = Tunnel::new(
                TunnelConfig {
                    tun,
                    upstreams,
                    monitor,
                },
                Arc::clone(&rules),
            )?;
            let handle = TunnelHandle {
                stopper: tunnel.stopper(),
                tunnel: Mutex::new(Some(tunnel)),
                rules,
                final_directory,
            };
            Ok(Box::into_raw(Box::new(handle)) as jlong)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Filters until stopped or until the tunnel or the network fails, and returns why.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_runTunnel<'caller>(
    _env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    handle: jlong,
) -> jint {
    // SAFETY: the Kotlin side only runs a live handle.
    let handle = unsafe { tunnel_handle(handle) };
    let tunnel = handle
        .tunnel
        .lock()
        .ok()
        .and_then(|mut tunnel| tunnel.take());
    tunnel.map_or(exit_code(Exit::Stopped), |mut tunnel| {
        exit_code(tunnel.run())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_stopTunnel<'caller>(
    _env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    handle: jlong,
) {
    // SAFETY: the Kotlin side only stops a live handle.
    unsafe { tunnel_handle(handle) }.stopper.stop();
}

/// Loads the final list again, for the running tunnel to use from its next query.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_reloadTunnelRules<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    handle: jlong,
) {
    // SAFETY: the Kotlin side only reloads a live handle.
    let handle = unsafe { tunnel_handle(handle) };
    unowned_env
        .with_env(|_env| -> Result<_, BridgeError> {
            let list = Arc::new(BlockList::load(&handle.final_directory)?);
            if let Ok(mut rules) = handle.rules.write() {
                *rules = list;
            }
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Frees a handle whose tunnel is not running.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_adaway_engine_Engine_destroyTunnel<'caller>(
    _env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    handle: jlong,
) {
    // SAFETY: the handle comes from `createTunnel` and the Kotlin side destroys it once.
    drop(unsafe { Box::from_raw(handle as *mut TunnelHandle) });
}
