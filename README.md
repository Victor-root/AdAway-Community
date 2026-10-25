> [!IMPORTANT]
> **AdAway 7 Alpha is out: pull requests are open again**
>
> AdAway has been fully rewritten in Kotlin and Rust, and version 7 is now available as a pre-release in [Releases](https://github.com/Victor-root/AdAway-Community/releases).
>
> **Testers are very welcome!** It is an alpha, so expect bugs: please report what you find in the issues, with your device and Android version.
>
> **Contributions** must be based on the new code, on the `adaway-rust` branch. Pull requests on the old 6.x code can no longer be merged.
>
> Thank you for your support!

# ![AdAway logo](app/src/main/res/mipmap-mdpi/icon.png) AdAway Community
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
    src="https://github.com/user-attachments/assets/96ec14cc-c16b-40f4-91fb-6ad999e3e4f3"
    width="360"
    alt="AdAway Community mobile home screen"
  />
</p>

<p align="center"><strong>📺 Android TV</strong></p>
<p align="center">
  <img
    src="https://github.com/user-attachments/assets/76e795e3-8f9d-4e99-bf6b-3f6c45b18b34"
    width="720"
    alt="AdAway Community Android TV home screen"
  />
</p>

---

## 📥 Download and updates

<p align="center">
  <a href="https://victor-root.github.io/Omnify/add.html?url=https%3A%2F%2Fgithub.com%2FVictor-root%2FAdAway-Community"><img src="https://victor-root.github.io/Omnify/assets/get-it-on-omnify.svg" alt="Get it on Omnify"/></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/Victor-root/AdAway-Community/releases"><img src="docs/badges/get-it-on-github.svg" alt="Get it on GitHub"/></a>
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

<details>
<summary><strong>✅ Tested</strong></summary>

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

</details>

---

## 📺 Android TV support

This fork also includes Android TV support, written for this fork and maintained in the same codebase as the mobile app.

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

## 🧪 Feedback

Feedback is welcome, especially for VPN-mode stability, Android TV behavior and recent Android/OEM compatibility issues.

See [CONTRIBUTING.md](CONTRIBUTING.md) for what to include in a report so it's as easy as possible to reproduce and fix.

---

## 🌍 Translations

**AdAway Community** speaks **65 languages** 🗣️, entirely thanks to the community of volunteer translators. 💜

Spotted a missing string or an awkward wording? Contributions to start a new language or improve an existing one are always welcome: every little bit helps! 🙌

<details>
<summary><strong>📊 Translation progress</strong></summary>

> **Legend**: 🟩 `80–100%` · 🟨 `50–79%` · 🟧 `25–49%` · 🟥 `0–24%`

| Language | Progress | Done |
|:---------|:---------|-----:|
| 🇬🇧 English (source) | 🟩🟩🟩🟩🟩🟩🟩🟩🟩🟩 | **100%** |
| 🇫🇷 French | 🟩🟩🟩🟩🟩🟩🟩🟩🟩🟩 | **99%** |
| 🇳🇱 Dutch | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **90%** |
| 🇩🇪 German | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **90%** |
| 🇮🇹 Italian | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **90%** |
| 🇵🇹 Portuguese | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **90%** |
| 🇪🇸 Spanish | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **90%** |
| 🇧🇬 Bulgarian | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇨🇳 Chinese (Simplified) | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇹🇼 Chinese (Traditional) | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇬🇷 Greek | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇮🇱 Hebrew | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇧🇷 Portuguese (Brazil) | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇸🇰 Slovak | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **89%** |
| 🇸🇦 Arabic | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🌐 Catalan | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇮🇩 Indonesian | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇯🇵 Japanese | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇵🇱 Polish | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇷🇺 Russian | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇸🇮 Slovenian | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇹🇷 Turkish | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **88%** |
| 🇨🇿 Czech | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **85%** |
| 🇻🇳 Vietnamese | 🟩🟩🟩🟩🟩🟩🟩🟩🟩⬜ | **85%** |
| 🇭🇷 Croatian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **84%** |
| 🇰🇷 Korean | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **84%** |
| 🇸🇪 Swedish | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **84%** |
| 🇺🇦 Ukrainian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **84%** |
| 🇧🇾 Belarusian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **83%** |
| 🇭🇺 Hungarian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **83%** |
| 🇷🇴 Romanian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **82%** |
| 🇱🇹 Lithuanian | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **81%** |
| 🇲🇽 Spanish (Mexico) | 🟩🟩🟩🟩🟩🟩🟩🟩⬜⬜ | **81%** |
| 🌐 Basque | 🟨🟨🟨🟨🟨🟨🟨🟨⬜⬜ | **76%** |
| 🇮🇷 Persian | 🟨🟨🟨🟨🟨🟨🟨⬜⬜⬜ | **70%** |
| 🇫🇮 Finnish | 🟨🟨🟨🟨🟨🟨⬜⬜⬜⬜ | **58%** |
| 🇪🇪 Estonian | 🟧🟧🟧🟧🟧⬜⬜⬜⬜⬜ | **47%** |
| 🇦🇫 Pashto | 🟧🟧🟧🟧🟧⬜⬜⬜⬜⬜ | **45%** |
| 🇮🇳 Malayalam | 🟧🟧🟧🟧⬜⬜⬜⬜⬜⬜ | **44%** |
| 🇹🇭 Thai | 🟧🟧🟧🟧⬜⬜⬜⬜⬜⬜ | **44%** |
| 🇩🇰 Danish | 🟧🟧🟧🟧⬜⬜⬜⬜⬜⬜ | **42%** |
| 🌐 Asturian | 🟧🟧🟧⬜⬜⬜⬜⬜⬜⬜ | **31%** |
| 🇦🇿 Azerbaijani | 🟧🟧🟧⬜⬜⬜⬜⬜⬜⬜ | **31%** |
| 🌐 Galician | 🟧🟧🟧⬜⬜⬜⬜⬜⬜⬜ | **29%** |
| 🇳🇴 Norwegian Bokmål | 🟧🟧🟧⬜⬜⬜⬜⬜⬜⬜ | **25%** |
| 🇷🇸 Serbian | 🟧🟧🟧⬜⬜⬜⬜⬜⬜⬜ | **25%** |
| 🇿🇦 Afrikaans | 🟥🟥⬜⬜⬜⬜⬜⬜⬜⬜ | **24%** |
| 🇮🇳 Tamil | 🟥🟥⬜⬜⬜⬜⬜⬜⬜⬜ | **17%** |
| 🇰🇭 Khmer | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **14%** |
| 🇺🇿 Uzbek | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **12%** |
| 🇵🇭 Filipino | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **10%** |
| 🇧🇩 Bengali | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **7%** |
| 🇳🇴 Norwegian | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **7%** |
| 🇵🇰 Urdu | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **7%** |
| 🌍 Esperanto | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **6%** |
| 🌐 Kurdish | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **6%** |
| 🇦🇱 Albanian | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇲🇲 Burmese | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇮🇳 Hindi | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇮🇸 Icelandic | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇲🇾 Malay | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇳🇵 Nepali | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇮🇳 Punjabi | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇱🇰 Sinhala | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |
| 🇵🇭 Tagalog | 🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜ | **5%** |

</details>

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
https://github.com/AdAway/AdAway | Official website: https://adaway.org

Thanks to the original author, past maintainers, current maintainers, translators, hosts list maintainers and all contributors who made AdAway possible.

---

## ⚖️ License

AdAway Community is licensed under the GPLv3+, the same license as the original AdAway project.

When distributing APK builds, the corresponding source code is made available through this repository and its release tags/commits.

See [LICENSE.md](LICENSE.md) for the full license text.
