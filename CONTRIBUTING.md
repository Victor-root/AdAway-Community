# 🤝 Contributing to AdAway Community

Thanks for your interest in contributing to **AdAway Community**! 🚀

---

## 🐛 Before opening an issue

Please check whether your problem is already reported. When reporting a bug, include as many details as possible: the more precise your report, the easier it is to reproduce and fix. 🙏

General details:
* 📱 Device model, Android version, ROM/manufacturer skin
* 📺 Device type: phone, tablet, TV or TV box · 🛡️ AdAway mode: VPN or root
* 🔁 Exact steps to reproduce, what you expected vs. what actually happened
* 🖼️ Screenshots/recordings, and Logcat logs if the issue still happens

If it's a VPN issue, also mention:
* 🔄 Does it start/stop by itself, restart after manual disable, or loop reconnecting?
* 📶 Does it happen after a network change (Wi-Fi/mobile/Ethernet)? Always-on VPN or another VPN app active?
* 🔋 Battery optimizations disabled for the app? 🧭 Do UI/notification/Quick Settings tile stay in sync?
* 🔌 Autostart at boot ("Enable at startup") on or off?
* 🆚 Happens on official AdAway too, or only Community?

If it's an Android TV issue, also mention:
* 📺 TV/box model and Android TV/Google TV version
* 🎮 Does remote/D-pad navigation work, and does the app show in the launcher?
* 🔐 Does the VPN permission prompt appear correctly? 📋 Does the DNS monitor work?

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

## 🔧 Pull requests

Pull requests are welcome! 🎉 Please keep each one focused on a single, clear change: one bug fix, one feature, one cleanup.

Good pull requests:
* 🎯 fix one clear problem, and avoid unrelated refactors
* 📱 keep mobile behavior working · 📺 keep Android TV behavior working
* 🛡️ avoid breaking VPN mode · #️⃣ avoid breaking root mode
* 🧪 include tests when practical, and explain what was tested manually

Please avoid mixing unrelated changes into the same pull request. Opening several PRs, though, is very welcome if that's what it takes to keep each one focused: working on three unrelated fixes? Three small PRs are much easier (and faster) to review than one big one, and there's no limit on how many you can open at once.

Examples:
* ✅ good: VPN restart fix
* ✅ good: Android TV layout fix
* ✅ good: translation fix
* ✅ also good: the three above, as three separate PRs from the same person
* ⚠️ not ideal: VPN fix + UI redesign + dependency bump + donation changes, all in one PR

---

## 🤖 Contributing with AI assistance

Using AI tools (Claude, ChatGPT, Copilot, etc.) to contribute is **totally welcome**: it's not a problem, it's not frowned upon, it's actually encouraged.

That doesn't mean "one prompt, one PR" though: no vibe coding, where you fire off a prompt and open a PR with whatever comes out without understanding or checking it. Stay in the driver's seat: understand the problem, guide the AI, review and iterate on what it produces. It isn't perfect, and code that looks right may not be, so everything needs to be tested in detail before submitting: "it compiles" is not a test.

When you prompt a fix, explicitly ask for a **surgical**, targeted change to the exact problem: no unsolicited refactors or cleanup. A small, clean diff is easier to get with a good prompt, and easier to review.

No issue letting the AI write the PR title and description, as long as it stays **readable by a human**: what problem it solves, what the fix does, without code-level detail (that's for me to see in review). Also mention whether the PR was AI-assisted, for transparency, and if you can, briefly describe your AI workflow (tool used, how you verified it): optional, but it helps calibrate the review.

**🧪 Testing is by far the most important part of a PR, even more so with AI.** VPN behavior on Android is finicky (manufacturer, battery management, Doze, network): a stability bug can take days to show up, so 5 minutes of use proves nothing.

For any VPN-related change, budget at least **a full week of real daily use** before opening the PR. In the Testing section, include:
* 📱 Device, Android version, VPN or root, mobile or TV
* 📅 Real duration and conditions: days of daily use, network changes encountered, phone reboots

---

## 🙏 Thanks

Every useful bug report, test result, translation, fix or review helps.

Even small contributions matter. 🚀
