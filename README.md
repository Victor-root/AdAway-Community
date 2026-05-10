# ![AdAway logo](app/src/main/res/mipmap-mdpi/icon.png) AdAway Community

> ⚠️ **Unofficial community-maintained fork of AdAway**  
> Keeping AdAway usable with community fixes while upstream maintenance activity is limited.

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](/LICENSE.md)
[![GitHub Downloads](https://img.shields.io/github/downloads/Victor-root/AdAway/total?logo=github)](https://github.com/Victor-root/AdAway/releases)
[![Upstream PR](https://img.shields.io/badge/upstream%20PR-4255-blue?logo=github)](https://github.com/AdAway/AdAway/pull/4255)

AdAway Community is an unofficial fork of [AdAway](https://github.com/AdAway/AdAway), an open source ad blocker for Android using the hosts file and a local VPN.

The official project is still the original source of AdAway, but upstream releases and PR reviews have been very limited for a while. The last stable upstream release is currently `v6.1.4`, published in October 2024.

This fork exists to keep useful fixes available instead of letting them sit unused while waiting for upstream review.

It is **not affiliated with, endorsed by, or signed by** the official AdAway maintainers.

If upstream becomes active again and equivalent fixes are merged/released, this fork may be deprecated or removed.

<p align="center">
  <img
    src="https://github.com/user-attachments/assets/c34cb045-9054-4e2f-bea8-057263f581d3"
    width="360"
    alt="AdAway Community screenshot"
  />
</p>

---

## 🚀 Goal

This fork aims to:

- 🧯 fix real bugs affecting daily usage;
- 📱 improve compatibility with recent Android versions and OEM ROMs;
- 🔄 provide tested builds while upstream review is pending;
- 🛠️ keep changes practical, minimal and maintainable;
- 🤝 remain compatible with upstream whenever possible.

This is not a rewrite and not a hostile fork.  
It is a best-effort community-maintained build.

---

## 🛠️ Current focus

The current release mainly focuses on VPN-mode stability.

It improves cases where AdAway VPN could:

- restart after being manually disabled;
- stop or desynchronize unexpectedly in the background;
- enter unstable restart/reconnect loops;
- rebuild the VPN tunnel too often during network changes;
- show inconsistent states between the app UI, Android VPN status, notification and Quick Settings tile.

Related upstream PR:

https://github.com/AdAway/AdAway/pull/4255

---

## 🔧 Current changes

Main changes currently included:

- 🧠 separate VPN user intent from runtime VPN service state;
- 🚫 prevent background/internal sync or update paths from silently restarting the VPN;
- 🛑 prevent sticky service restart from resurrecting VPN mode against user intent;
- 🔄 improve synchronization between VPN service state, app UI, notification and Quick Settings tile;
- 📶 avoid unnecessary VPN tunnel rebuilds when secondary networks flicker;
- ⚡ reset VPN reconnect throttling for explicit user starts;
- 🧯 reduce unstable restart/reconnect behavior in VPN mode;
- ✅ keep the welcome/setup flow working on first launch;
- 🧩 include a small German XML resource fix needed during testing.

---

## 📱 Tested on

Current release tested on:

- Oppo Reno 13 Pro;
- Android 16 / ColorOS;
- AdAway VPN mode;
- daily usage for two weeks.

Observed result:

- ✅ VPN no longer restarted by itself after manual disable;
- ✅ VPN stayed stable during normal background usage;
- ✅ no visible VPN restart/reconnect loop during daily use;
- ✅ Quick Settings tile stayed coherent;
- ✅ app UI, Android VPN key icon and VPN state stayed consistent;
- ✅ Wi-Fi / mobile data changes did not cause unwanted VPN restarts;
- ✅ local-network IoT connectivity stayed stable;
- ✅ Ecovacs Home robot vacuum stayed reachable through the patched VPN.

---

## 📦 Download

Unofficial community builds:

https://github.com/Victor-root/AdAway/releases

---

## ⚠️ Installation notes

This APK is signed with my own signing key.

Because of that, it **cannot be installed as an update over the official AdAway build**.

You need to uninstall the official AdAway app before installing this APK.

⚠️ Uninstalling the official app may remove local settings.  
Check your configuration before doing it.

---

## 🧪 Feedback

Feedback is welcome, especially for VPN-mode instability or recent Android compatibility issues.

Useful details:

- device model;
- Android version;
- ROM / manufacturer skin;
- VPN mode or root mode;
- autostart enabled or disabled;
- battery/background restrictions;
- whether the VPN restarts after manual disable;
- whether UI / notification / Quick Settings tile stay coherent;
- Logcat logs if the issue still happens.

---

## 🧱 Requirements

- Android 8 Oreo or above;
- VPN mode or root mode depending on your setup;
- Android VPN permission for VPN mode.

For devices older than Android 8 Oreo, use older official AdAway versions from upstream.

---

## 🔐 Permissions

AdAway uses these permissions:

- `INTERNET` to download hosts files and application updates;
- `ACCESS_NETWORK_STATE` to monitor network changes;
- `RECEIVE_BOOT_COMPLETED` to optionally start AdAway after boot;
- `FOREGROUND_SERVICE` to run the VPN service in foreground;
- `POST_NOTIFICATIONS` for source update, app update and VPN control notifications;
- `REQUEST_INSTALL_PACKAGES` for the built-in updater;
- `QUERY_ALL_PACKAGES` to let users exclude apps from VPN.

---

## 🤝 Credits

AdAway was created and maintained by the official AdAway project contributors.

Official project:

https://github.com/AdAway/AdAway

Official website:

https://adaway.org

Thanks to the original author, past maintainers, current maintainers, translators, hosts list maintainers and all contributors who made AdAway possible.

This fork keeps the original license and credits.

---

## ⚖️ License

AdAway is licensed under the GPLv3+.

See [LICENSE.md](LICENSE.md) for the full license text.
