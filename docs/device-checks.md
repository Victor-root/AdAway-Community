# Device checks

Everything changed since 6.6.1-c builds, passes the unit tests
and lint, and the native changes were also tested on a Linux host. The checks below need a running
Android system: run them on an emulator or a device before the next release.

## Results on the Android 14 emulator (2026-10-13)

Checked (`[x]` below) on the `google_apis` x86_64 image, with the release APK (R8, signed with the
debug key) and the debug APK:

- All 14 instrumented tests pass, `MigrationTest` included once the real version 4 schema was restored
  (the exported `4.json` held the version 5 schema since 2020).
- VPN mode: the `specialUse` foreground service starts, ad hosts don't resolve while others do, Pause
  and Resume from the notification, the automation broadcast from a root shell, a restart at boot,
  Wi-Fi ↔ cellular switches and a full network loss all work.
- The DNS log records queries, and blocking then allowing the same host updates one user list entry.
- Root mode binaries, run from the adb root shell (the image has no `su` for apps): tcpdump 4.99.7
  records the DNS queries in the format the app parses and stops with `killall`; the web server
  answers on ports 80 and 443 over TLS 1.2 and 1.3 with the existing certificate (same SHA-256).
- Preferences: titles, and the D-pad skipping the info rows and the dimmed root section.
- Found and fixed: on a fresh install, the AdAway and Pete Lowe default sources stayed empty for
  good, one broken source among usable ones reported "none of your hosts sources are reachable", and
  accepting the VPN consent again on the mobile home didn't restart ad blocking.

Root mode, on the same image rooted with rootAVD (Magisk 26.4) and the Magisk systemless hosts
module enabled:

- The hosts file is applied (72,548 hosts, `root:root 644`, `system_file` label) and reverted, blocked
  hosts resolve to 127.0.0.1, and it stays applied after a reboot.
- The web server starts from Preferences without `LD_LIBRARY_PATH` and again at boot, answers on
  ports 80 and 443, and its state reads "Running but certificate not installed"; the certificate is
  saved as the same file and the dialog opens the security settings.
- The DNS log records queries through tcpdump and stops it.
- "Open hosts file" offers the viewers and serves the whole file through the `FileProvider`; "Test
  web server" opens Chrome, and with no browser shows a message instead of crashing.
- Without the systemless hosts module, applying fails with the "Magisk systemless hosts" advice and
  the log names the read-only partition (`mount` exits with 0 even when it cannot remount).

Still to check: Android TV (needs a TV image), Ethernet (the emulator has none), the in-app update
download (needs a newer release), the web server certificate trusted by the system.

## Emulator setup

An Android 14 image with `adb root` support (`google_apis`) covers both ad blocking methods:

```sh
sdkmanager "platform-tools" "emulator" "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n adaway-34 -k "system-images;android-34;google_apis;x86_64"
emulator -avd adaway-34 &
adb wait-for-device && adb root
./gradlew installDebug installRelease  # the release APK needs keystore.properties to be signed
```

Root mode needs a rooted system: on the `google_apis` image, `adb root` gives a root shell for the
commands below, but the app itself needs `su` (for example the emulator rooted with Magisk/rootAVD)
to apply the hosts file, run the web server and record the DNS log.

## 1. Automated tests

- [x] `./gradlew connectedDebugAndroidTest` passes, in particular:
  - `MigrationTest`: every database migration (1 → 8) against the exported schemas.
  - `UserListTest.testUpsertUserItemReplacesSameHost`: adding a host already in a user list
    replaces it instead of adding a second entry.

## 2. VPN mode (release APK)

The release build is the one that matters here: it is shrunk by R8, with dnsjava 3.6.5.

- [x] Turning ad blocking on starts the VPN without a crash on Android 14: it runs as a
      `specialUse` foreground service (targetSdk 34).
- [x] Blocked hosts don't resolve, allowed ones do, redirected ones resolve to their redirection.
- [x] The VPN notification Pause and Resume actions work.
- [ ] Killing the VPN (`adb shell am force-stop org.adaway`, or stopping it from the system VPN
      settings) brings the home screen and the quick settings tile back to "off": the service now
      reports its status to `VpnModel` directly instead of through `LocalBroadcastManager`.
- [x] "Enable at startup" restarts the VPN after a reboot: `BootReceiver` is no longer exported.
- [x] The DNS log records queries, and adding a host from it that is already in a list updates that
      entry (no second entry in Your lists).

## 3. Root mode (rooted emulator or device, debug and release APKs)

- [x] Applying and reverting the hosts file works (the `dd`, `chown` and `chmod` commands now quote
      their paths).
- [x] The web server starts (Preferences → Root → web server), shows as running, and
      `https://localhost/internal-test` loads in a browser once its certificate is installed. It now
      uses Mbed TLS instead of OpenSSL, is statically linked and is started without
      `LD_LIBRARY_PATH`. Also check from a root shell:
      `curl -k https://localhost/internal-test` (or `openssl s_client -connect localhost:443`).
- [ ] The certificate installed with a previous version is still trusted (same certificate).
- [x] The DNS log (tcpdump 4.99.7 / libpcap 1.10.7) records the hosts of the device's DNS queries,
      and stops when asked (the app finds it with `ps -A | grep` and stops it with `killall`).
- [x] Debug builds can now do all of the above: they didn't package the tcpdump and web server
      executables before.

## 4. Screens

- [ ] Preferences, Update and Backup screens show their title in the app bar, and the D-pad skips
      disabled rows (e.g. the root section in VPN mode) and the non-selectable info rows
      (`PrefsFragment` / `FocusSkippingPreferenceGroupAdapter`).
- [x] Hosts sources, on mobile and TV: adding, editing and deleting a URL source and a file source
      (file picker), the validation errors, the "Add source" / edit titles, and the status and host
      count of each source (now shared code; counts from 1000 hosts show as "1k").
- [ ] Your lists: adding and editing blocked, allowed (wildcards) and redirected hosts.
- [ ] Android TV home: the version label reads `v6.6.1-c`; the sync error dialog offers "Retry sync"
      for download and connection errors (mobile and TV share it now).
- [ ] The launcher icon (now WebP) shows correctly, with the adaptive and the legacy launchers.

## 5. App update

- [ ] The in-app update downloads and installs (Update screen). `UpdateModel` registers its
      `ACTION_DOWNLOAD_COMPLETE` receiver with `RECEIVER_NOT_EXPORTED`, but that broadcast comes from
      the Download Manager app, not from the system: check it is received on Android 13 and 14, and
      switch the receiver to `RECEIVER_EXPORTED` if not.
- [ ] After an update, the "app installed" toast and notification show on Android TV when the app is
      not opened (`UpdateReceiver`, `MY_PACKAGE_REPLACED`, no longer exported).

## 6. Automation

- [ ] From a root shell, `am broadcast -a org.adaway.action.SEND_COMMAND -p org.adaway --es COMMAND STOP`
      then `START` turns ad blocking off and on (see [automation.md](automation.md)).

## Outside the app

- [ ] F-Droid shows the store listing translations from the BCP 47 folders (`metadata/zh-TW`,
      `metadata/he`, `metadata/id`), and its build doesn't flag the APK anymore
      (`dependenciesInfo` disabled).
- [ ] The next Transifex sync writes the Hebrew and Indonesian store listings to `metadata/he` and
      `metadata/id`, and doesn't complain about the removed `strings_support.xml`.
- [ ] GitHub Actions are enabled on the fork (Actions tab), so `Android CI` runs on push.

## 7. Network, download and crash fixes

- [ ] VPN mode on Ethernet (an Android TV box, or the emulator with Wi-Fi turned off in its extended
      controls): ad blocking works, unplugging and plugging back the cable reconnects the tunnel,
      and turning Wi-Fi on and off while Ethernet stays up doesn't stop it.
- [x] Root mode, Preferences → Root → Open hosts file: a text viewer opens the hosts file instead of
      the app crashing (it now goes through the `FileProvider`, read-only).
- [x] A hosts source whose URL answers 404 (e.g. a typo in a GitHub raw URL) is reported as failed
      and keeps its previous hosts.
- [ ] Update screen: cancelling the download from its notification, or a failed download, shows
      "Download failed" and the Update button again.
- [x] On a device without a browser (most Android TV): DNS log → open in browser, Help → About links
      and the web server test page show "No application found to open it" instead of crashing.
- [x] Mobile home in VPN mode with the VPN consent revoked: declining the consent dialog doesn't
      show it again until the app is reopened.
- [x] The host counters on the home screens show grouped digits (e.g. 123,456).
- [ ] Root mode web server certificate install (Android 10 and below) still opens the system
      installer (the certificate is now parsed with `java.security.cert`).
