# 🤝 Contributing to AdAway Community

Thanks for your interest in contributing to **AdAway Community**! 🚀

AdAway Community is an unofficial community-maintained fork of [AdAway](https://github.com/AdAway/AdAway).

The goal is simple: keep the app usable, stable and modern with practical fixes for recent Android versions, VPN stability and Android TV support while upstream activity is limited.

---

## 🐛 Before opening an issue

Please check whether your problem is already reported.

When reporting a bug, include **as many details as possible**.
The more precise your report is, the easier it is to reproduce, understand and fix. 🙏

Useful details:

* 📱 Device model
* 🤖 Android version
* 🧩 ROM / manufacturer skin
* 📺 Device type: phone, tablet, TV or TV box
* 🛡️ AdAway mode: VPN mode or root mode
* 🌐 Network type: Wi-Fi, mobile data or Ethernet
* 🚀 Autostart enabled or disabled
* 🔋 Battery/background restrictions
* 🔁 Exact steps to reproduce the issue
* ✅ What you expected to happen
* ❌ What actually happened
* 🔄 Whether the VPN restarts after manual disable
* 🧭 Whether UI / notification / Quick Settings tile stay coherent
* 🖼️ Screenshots or screen recordings if useful
* 📜 Logcat logs if the issue still happens

Useful Logcat filters:

```text
AdAway
VpnService
VpnModel
VpnWorker
VpnConnectionMonitor
AdBlockingTileService
```

---

## 🛡️ Reporting VPN issues

For VPN stability bugs, please describe:

* 🔁 Does the VPN start by itself?
* 🛑 Does the VPN stop by itself?
* 🔄 Does the VPN enter a reconnect loop?
* 📶 Does it happen after Wi-Fi / mobile data / Ethernet changes?
* 🔐 Is Always-on VPN enabled?
* 🧩 Is another VPN app installed or active?
* 🔋 Are battery optimizations disabled for AdAway Community?
* 🆚 Does the issue happen on official AdAway, AdAway Community, or both?

The more context you provide, the less guesswork is needed.

---

## 📺 Reporting Android TV issues

For Android TV bugs, please include:

* 📺 TV / box model
* 🤖 Android TV / Google TV version
* 🎮 Remote type if relevant
* 🏠 Whether the app appears in the launcher
* ⬆️⬇️ Whether D-pad navigation works
* 🔐 Whether the VPN permission prompt appears correctly
* 📋 Whether the DNS monitor works
* 🌐 Whether the device uses Wi-Fi or Ethernet

Android TV setups vary a lot, so device-specific details are very useful.

---

## 🔧 Pull requests

Pull requests are welcome! 🎉

Please keep them focused and easy to review.

Good pull requests:

* 🎯 fix one clear problem
* 🧹 avoid unrelated refactors
* 📱 keep mobile behavior working
* 📺 keep Android TV behavior working
* 🛡️ avoid breaking VPN mode
* #️⃣ avoid breaking root mode
* 🧪 include tests when practical
* ✅ explain what was tested manually

Please avoid mixing unrelated changes in the same pull request.

Examples:

* ✅ good: VPN restart fix
* ✅ good: Android TV layout fix
* ✅ good: translation fix
* ⚠️ not ideal: VPN fix + UI redesign + dependency bump + donation changes in one PR

Small, focused PRs are much easier to review and merge.

---

## 🧑‍💻 Coding guidelines

Try to follow the style already used in the project.

Before submitting changes, run what you can:

```bash
./gradlew test
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

If the build or tests fail because of your local environment, mention it clearly.

Useful things to mention in a PR:

* 🧪 tests run
* 📱 device used for manual testing
* 🤖 Android version
* 📺 whether Android TV was tested
* 🛡️ whether VPN mode was tested
* #️⃣ whether root mode was tested, if relevant

---

## 📦 APK updater

The old APK self-updater UI is hidden in AdAway Community for now because it originally pointed to official AdAway infrastructure.

Host source / block-list updates are still part of the app and should not be confused with APK updates.

If you work on app updates, please keep the Community fork separate from the official AdAway update infrastructure.

Future updater work should ideally use AdAway Community GitHub releases.

---

## 🧹 Donations / sponsorship links

Donation and sponsor references have been removed from the app UI in this fork.

Please do not reintroduce donation dialogs, sponsor buttons, crypto QR codes or similar funding UI without prior discussion.

Credits and license notices must remain. ❤️

---

## 🙏 Thanks

Every useful bug report, test result, translation, fix or review helps.

Even small contributions matter. 🚀
