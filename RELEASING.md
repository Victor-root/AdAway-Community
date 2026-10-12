# Releasing

## 1. Checking bugs and technical debt

### Lint checks

Android development tools provide linter to check common errors.
Use `./gradlew :app:lintRelease` to run the linter and produce (human readable) reports as HTML file located at `app/build/reports/lint-results-release.html`.

> [!IMPORTANT] 
> Check no new warning was introduced before releasing.

## 2. Updating application version

Each version has its own number that follows the [Semantic Versioning](https://semver.org/) principle (starting from version 4).

> [!IMPORTANT]
> Update application version name (`appName`) and code (`appCode`) from the `gradle/libs.versions.toml` catalog file.

## 3. Updating the changelog

The AdAway project provides [a global changelog](CHANGELOG.md).

> [!IMPORTANT]
> Update the changelog to let users know what is inside each new version before releasing it.

## 4. Building release APK

The release apk must be built using the `release` flavor (not `debug`).
Building needs JDK 17 or later (CI uses JDK 21), the Android SDK with platform 34 and NDK `27.2.12479018` (the native tcpdump and web server modules), with `sdk.dir` set in `local.properties`.
Run `./gradlew test assembleRelease`; the APK is written to `app/build/outputs/apk/release/`.

The build already names the APK `AdAway.Community.v<version_name>.apk`, the format of the published GitHub releases (e.g. _AdAway.Community.v6.6.1-c.apk_).

## 5. Distributing release

Before sharing the any release, remember to test it.
Release variant apk does not behave like debug variant.
Same goes for real device versus emulator.

> [!IMPORTANT]
> Final tests should be done with release apk variant on real device.

Once tested, releases are posted on XDA development thread using the following template:
```
Hi all,

<welcoming message about the new version>

[U][SIZE="4"]Changelog:[/SIZE][/U]
[LIST]
[*] Item 1
[*] Item 2
[*] ...
[*] Item n
[/LIST]

[U][SIZE="4"]Thanks:[/SIZE][/U]

Special thanks to <contributors> for theirs contributions and <bug reporters> for theirs helpful bug reports.

[U][SIZE="4"]Download:[/SIZE][/U]

[URL="https://app.adaway.org/adaway.apk"]AdAway <application version>[/URL]
```

### Beta releases

The beta releases are only announced in the XDA development thread.

### Stable releases

The stable releases are distributed through [GitHub releases](https://github.com/AdAway/AdAway/releases) and [F-Droid store](https://f-droid.org/packages/org.adaway/) and are posted of the first post of XDA development thread.
Once ready, create and push a tag on GitHub repository using `vX.Y.Z` format (or `vX.Y.Zb` for pre-releases).
To publish the application in GitHub:

* Create a new version based on this tag,
* Copy the changelog part related to the version as description of the release,
* Upload apk binary to the release.

Pushing a tag will publish the application to F-Droid store.
It might takes some days to update but if it does not, build logs are available at the following address: `https://monitor.f-droid.org/builds/log/org.adaway/<versioncode>`.
