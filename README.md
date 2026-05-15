# ![AdAway logo](app/src/main/res/mipmap-mdpi/icon.png) AdAway Community

> ⚠️ **Unofficial community-maintained fork of AdAway**  
> A practical community fork focused on keeping AdAway usable on recent Android versions, with mobile VPN stability fixes and Android TV support.

<div align="center">

[![Latest release](https://img.shields.io/github/v/release/Victor-root/AdAway-Community?style=for-the-badge&logo=github&label=release)](https://github.com/Victor-root/AdAway-Community/releases)
[![Downloads](https://img.shields.io/github/downloads/Victor-root/AdAway-Community/total?style=for-the-badge&logo=github&label=downloads)](https://github.com/Victor-root/AdAway-Community/releases)
[![Last update](https://img.shields.io/github/last-commit/Victor-root/AdAway-Community/main?style=for-the-badge&logo=git&label=last%20update)](https://github.com/Victor-root/AdAway-Community/commits/main)

[![Unofficial fork](https://img.shields.io/badge/Unofficial-community%20fork-f59e0b?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Victor-root/AdAway-Community)
[![Android APK](https://img.shields.io/badge/Android-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Victor-root/AdAway-Community/releases)
[![Android TV](https://img.shields.io/badge/Android%20TV-supported-3DDC84?style=for-the-badge&logo=androidtv&logoColor=white)](https://github.com/Victor-root/AdAway-Community#android-tv-support)
[![VPN stability](https://img.shields.io/badge/VPN-stability%20fixes-2563eb?style=for-the-badge&logo=wireguard&logoColor=white)](https://github.com/Victor-root/AdAway-Community#mobile--vpn-stability)


</div>
AdAway Community is an unofficial fork of [AdAway](https://github.com/AdAway/AdAway), an open source ad blocker for Android using hosts sources and a local VPN / root-based blocking depending on the selected mode.

This fork is **not affiliated with, endorsed by, or signed by** the official AdAway maintainers.

---

<p align="center"><strong>📱 Mobile</strong></p>
<p align="center">
  <img
    src="https://github.com/user-attachments/assets/5bd9cce9-b488-435b-a008-578b126238b1"
    width="360"
    alt="AdAway Community — mobile home screen"
  />
</p>

<p align="center"><strong>📺 Android TV</strong></p>
<p align="center">
  <img
    src="https://github.com/user-attachments/assets/f4c2e33b-7d72-4af1-ae60-aea3dad09f9a"
    width="720"
    alt="AdAway Community — Android TV home screen"
  />
</p>

---

## 🚀 Why this fork exists

AdAway is a great project with a long history, but upstream release and review activity has been limited for a while.

Some bugs and pull requests have remained open for a long time, including issues affecting VPN-mode stability on recent Android versions and OEM ROMs.

This fork exists to keep the project usable with community-maintained fixes instead of letting tested improvements sit unused.

The goal is simple:

- 🧯 fix real bugs affecting daily usage;
- 📱 keep the mobile Android experience stable;
- 📺 maintain Android TV support in the same codebase;
- 🔄 publish tested community builds;
- 🛠️ keep changes practical and maintainable;
- 🤝 stay compatible with upstream whenever possible.

This is not a hostile fork and not a claim of ownership over the original project.

If the official project becomes actively maintained again and equivalent fixes are merged/released, this fork may be deprecated, archived, or re-aligned with upstream.

---

## 🆕 Current community version

Current community version:

`6.5.0-c`

The `c` stands for **Community**.

This version includes:

- 📱 mobile VPN stability fixes;
- 📺 Android TV integration;
- 🧩 small build/runtime fixes needed during testing;
- 🔧 version naming separated from the official AdAway releases.

---

## 📱 Mobile / VPN stability

This fork includes a VPN-mode stability patch originally proposed upstream here:

https://github.com/AdAway/AdAway/pull/4255

It is intended to improve cases where AdAway VPN could:

- restart after being manually disabled;
- stop or desynchronize unexpectedly in the background;
- enter unstable restart/reconnect loops;
- rebuild the VPN tunnel too often during network changes;
- show inconsistent states between the app UI, Android VPN status, notification and Quick Settings tile.

Main VPN-related changes:

- 🧠 separate VPN user intent from runtime VPN service state;
- 🚫 prevent background/internal sync or update paths from silently restarting the VPN;
- 🛑 prevent sticky service restart from resurrecting VPN mode against user intent;
- 🔄 improve synchronization between VPN service state, app UI, notification and Quick Settings tile;
- 📶 avoid unnecessary VPN tunnel rebuilds when secondary networks flicker;
- ⚡ reset VPN reconnect throttling for explicit user starts;
- 🧯 reduce unstable restart/reconnect behavior in VPN mode;
- ✅ keep the welcome/setup flow working on first launch;
- 🧪 add focused testable decision logic for VPN start behavior.

---

## 📺 Android TV support

This fork also includes Android TV support, based on community work from:

https://github.com/sunmasters/Adaway-For-TV

Thanks to the author of that fork for the original Android TV work.

Current TV-related features:

- 📺 Android TV / Leanback launcher support;
- 🖼️ TV banner resource;
- 🎮 D-pad / remote-friendly TV home screen;
- 🧭 automatic redirect to the TV UI when running on Android TV;
- 📋 TV-friendly DNS log screen;
- 🔘 TV actions for toggle, update, sync, DNS monitor and host sources.

Android TV support has been tested on my Android TV setup. More feedback from other TV devices is still welcome, especially for different remotes, launchers, boxes and network setups.

Known points where more feedback is useful:

- D-pad navigation with different remotes;
- VPN permission flow on different Android TV builds;
- DNS monitor behavior on TV;
- host sources screen usability on TV;
- Ethernet behavior on Android TV boxes.

---

## ✅ Tested

Current mobile testing:

- Oppo Reno 13 Pro;
- Android 16 / ColorOS;
- AdAway VPN mode;
- daily usage for multiple weeks.

Observed result on mobile:

- ✅ VPN no longer restarted by itself after manual disable;
- ✅ VPN stayed stable during normal background usage;
- ✅ no visible VPN restart/reconnect loop during daily use;
- ✅ Quick Settings tile stayed coherent;
- ✅ app UI, Android VPN key icon and VPN state stayed consistent;
- ✅ Wi-Fi / mobile data changes did not cause unwanted VPN restarts;
- ✅ local-network IoT connectivity stayed stable;
- ✅ Ecovacs Home robot vacuum stayed reachable through the patched VPN.

Android TV testing:

- ✅ Android TV UI launches correctly;
- ✅ TV home screen is usable with a remote;
- ✅ core TV navigation works on my setup.

More device feedback is welcome.

---

## 📦 Download

Unofficial community builds are available here:

https://github.com/Victor-root/AdAway-Community/releases

---

## ⚠️ Installation notes

This APK is signed with my own signing key.

Because of that, it **cannot be installed as an update over the official AdAway build**.

You need to uninstall the official AdAway app before installing this APK.

⚠️ Uninstalling the official app may remove local settings.  
Check your configuration before doing it.

Future AdAway Community updates should install normally as long as they are signed with the same Community signing key.

---

## 🧪 Feedback

Feedback is welcome, especially for:

- VPN-mode instability;
- recent Android compatibility issues;
- Android TV behavior;
- D-pad / remote navigation;
- network changes;
- Quick Settings tile / notification state synchronization.

Please include as many details as possible. The more precise the report is, the easier it is to reproduce, understand and fix the issue.

Useful details when reporting feedback:

- device model;
- Android version;
- ROM / manufacturer skin;
- phone, tablet, TV or TV box;
- VPN mode or root mode;
- Wi-Fi, mobile data or Ethernet;
- autostart enabled or disabled;
- battery/background restrictions;
- exact steps to reproduce the issue;
- what you expected to happen;
- what actually happened;
- whether the VPN restarts after manual disable;
- whether UI / notification / Quick Settings tile stay coherent;
- screenshots or screen recordings if useful;
- Logcat logs if the issue still happens.

For Logcat, useful filters may include:

- `AdAway`
- `VpnService`
- `VpnModel`
- `VpnWorker`
- `VpnConnectionMonitor`
- `AdBlockingTileService`

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

## 🧾 Legal / license compliance

AdAway Community is based on AdAway and remains licensed under the GPLv3+.

This fork keeps the original license and credits.

When distributing APK builds, the corresponding source code is made available through this repository and its release tags/commits.

This fork does not claim to be the official AdAway project.

---

## 🤝 Credits

AdAway was created and maintained by the official AdAway project contributors.

Official project:
https://github.com/AdAway/AdAway | Official website: https://adaway.org

Thanks to the original author, past maintainers, current maintainers, translators, hosts list maintainers and all contributors who made AdAway possible.

Special thanks also to the Android TV community fork work that helped bootstrap the TV integration:

https://github.com/sunmasters/Adaway-For-TV

---

## ⚖️ License

AdAway is licensed under the GPLv3+.

See [LICENSE.md](LICENSE.md) for the full license text.
