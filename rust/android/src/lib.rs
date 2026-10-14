//! JNI bindings exposing the engine to the Android app (`org.adaway.engine.Engine`).

use adaway_engine::{address, hostname};
use jni::EnvUnowned;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JObject, JString};
use jni::sys::jboolean;

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
            match hostname::normalize(&host, allow_wildcards) {
                Some(normalized) => JString::new(env, normalized),
                None => Ok(JString::null()),
            }
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
            match address::normalize_ip(&address) {
                Some(normalized) => JString::new(env, normalized),
                None => Ok(JString::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
