//! JNI bindings exposing the engine to the Android app (`org.adaway.engine.Engine`).

use std::fmt;
use std::io;
use std::path::{Path, PathBuf};

use adaway_engine::compile::{self, OutputDirectories, UserRules};
use adaway_engine::source::{self, ParseError, ParseOptions, SourceFormat};
use adaway_engine::{address, catalog, hostname};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JLongArray, JObject, JObjectArray, JString};
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
}

impl fmt::Display for BridgeError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BridgeError::Jni(error) => write!(formatter, "JNI error: {error}"),
            BridgeError::Io(error) => write!(formatter, "I/O error: {error}"),
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
