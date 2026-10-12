# Device checks

Everything changed since 6.6.1-c builds, passes the unit tests
and lint, and the native changes were also tested on a Linux host. The checks below need a running
Android system: run them on an emulator or a device before the next release.

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

- [ ] `./gradlew connectedDebugAndroidTest` passes, in particular:
  - `MigrationTest`: every database migration (1 → 7) against the exported schemas.
  - `UserListTest.testUpsertUserItemReplacesSameHost`: adding a host already in a user list
    replaces it instead of adding a second entry.

## 2. VPN mode (release APK)

The release build is the one that matters here: it is shrunk by R8, with dnsjava 3.6.5.

- [ ] Turning ad blocking on starts the VPN without a crash on Android 14: it runs as a
      `specialUse` foreground service (targetSdk 34).
- [ ] Blocked hosts don't resolve, allowed ones do, redirected ones resolve to their redirection.
- [ ] The VPN notification Pause and Resume actions work.
- [ ] Killing the VPN (`adb shell am force-stop org.adaway`, or stopping it from the system VPN
      settings) brings the home screen and the quick settings tile back to "off": the service now
      reports its status to `VpnModel` directly instead of through `LocalBroadcastManager`.
- [ ] "Enable at startup" restarts the VPN after a reboot: `BootReceiver` is no longer exported.
- [ ] The DNS log records queries, and adding a host from it that is already in a list updates that
      entry (no second entry in Your lists).

## 3. Root mode (rooted emulator or device, debug and release APKs)

- [ ] Applying and reverting the hosts file works (the `dd`, `chown` and `chmod` commands now quote
      their paths).
- [ ] The web server starts (Preferences → Root → web server), shows as running, and
      `https://localhost/internal-test` loads in a browser once its certificate is installed. It now
      uses Mbed TLS instead of OpenSSL, is statically linked and is started without
      `LD_LIBRARY_PATH`. Also check from a root shell:
      `curl -k https://localhost/internal-test` (or `openssl s_client -connect localhost:443`).
- [ ] The certificate installed with a previous version is still trusted (same certificate).
- [ ] The DNS log (tcpdump 4.99.7 / libpcap 1.10.7) records the hosts of the device's DNS queries,
      and stops when asked (the app finds it with `ps -A | grep` and stops it with `killall`).
- [ ] Debug builds can now do all of the above: they didn't package the tcpdump and web server
      executables before.

## 4. Screens

- [ ] Preferences, Update and Backup screens show their title in the app bar, and the D-pad skips
      disabled rows (e.g. the root section in VPN mode) and the non-selectable info rows
      (`PrefsFragment` / `FocusSkippingPreferenceGroupAdapter`).
- [ ] Hosts sources, on mobile and TV: adding, editing and deleting a URL source and a file source
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
