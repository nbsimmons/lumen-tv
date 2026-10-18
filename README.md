# Lumen

A Jellyfin and Seerr client for Fire TV and Android TV, built with Kotlin and Compose for TV. It's designed to feel calm and Apple-like while following each platform's remote conventions.

Runs on Fire OS 6 or later and Android TV / Google TV 7.1 or later. Tested on a Fire TV Stick 4K (2nd gen, Fire OS 8) and a TCL Google TV (Android 14); Fire OS 6 and 7 are supported but untested. Devices with under 1.6GB of RAM skip the weekly IMDb ratings download and use smaller image caches. The 2025–26 Vega OS sticks can't install Android apps.

## Requirements

- Jellyfin server 12.x (uses `jellyfin-core` 1.9.x)
- Seerr 3.x (optional, for Discover and requests)

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleSideloadRelease
```

The APK is written to `app/build/outputs/apk/sideload/release/app-sideload-release.apk`. The `store` flavour is the Amazon Appstore build: no YouTube extraction, no IMDb dataset and no self-updater.

Release builds are signed with the key described in `~/.lumen/signing.properties` (override the path with `LUMEN_SIGNING`). Without that file they fall back to the local debug key, which the release script refuses to publish.

## Releases and updates

The sideload build checks the GitHub repo in `lumen.updateRepo` (in `gradle.properties`) for a newer release when Home loads (at most every 6 hours) and from Settings › Check for Updates. It downloads the `*-sideload.apk` asset, checks that it's signed with the same key as the installed app, then hands it to Android's installer. Android always asks the viewer to confirm, and each device needs Lumen allowed to install unknown apps once.

To publish a release (needs `gh auth login` first):

```bash
scripts/release.sh 0.2.0 notes.md
```

It bumps the version in `gradle.properties`, builds and signs the APK, refuses a debug-signed build, and creates the `v0.2.0` GitHub release with the APK and its SHA-256. Commit the version bump afterwards.

**Back up `~/.lumen/` somewhere safe** (a password manager or encrypted drive), never in this repo. If the key is lost, installed copies can't be updated and every device has to uninstall and reinstall.

## Install on the Stick

1. On the Stick, open Settings › My Fire TV › About and click the device name 7 times to unlock Developer Options.
2. In Developer Options, turn on ADB Debugging.
3. Connect and install:

```bash
adb connect <stick-ip>:5555
adb install -r app/build/outputs/apk/sideload/release/app-sideload-release.apk
```

The first time you connect, the TV asks you to approve this computer.

## Remote controls

| Where | Key | Action |
|---|---|---|
| Anywhere | Back | Go up one level |
| Player | OK / Play-Pause | Play or pause |
| Player | Left / Right | Skip 10s (hold for 30s) |
| Player | Rewind / Fast-forward | Skip 10s / 30s |
| Player | Down / Menu | Audio and subtitle tracks |

## Layout

- `data/jellyfin`: SDK client, session, library queries
- `data/seerr`: Seerr REST client (cookie session, discover, search, requests with quality profiles)
- `playback`: device profile built from the Stick's real decoders and HDMI audio, playback info, progress reporting
- `ui/components`: design system (cards, shelves, backdrop, pills, text field)
- `ui/theme/Tokens.kt`: the single source of colours, spacing, shapes and motion
