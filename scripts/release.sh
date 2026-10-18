#!/usr/bin/env bash
# Builds, signs and publishes a sideload release to GitHub. Usage: scripts/release.sh 0.2.0 [notes.md]
set -euo pipefail

version="${1:?Usage: scripts/release.sh <version> [notes-file]}"
notes="${2:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/gradle.properties"
signing="${LUMEN_SIGNING:-$HOME/.lumen/signing.properties}"

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3" >&2; exit 1; }
command -v gh >/dev/null || { echo "Install the GitHub CLI: brew install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Sign in first: gh auth login" >&2; exit 1; }
[[ -f "$signing" ]] || { echo "Missing signing config at $signing" >&2; exit 1; }

repo="$(grep '^lumen.updateRepo=' "$props" | cut -d= -f2-)"
[[ -n "$repo" ]] || { echo "Set lumen.updateRepo=owner/name in gradle.properties" >&2; exit 1; }
current_code="$(grep '^lumen.versionCode=' "$props" | cut -d= -f2)"
next_code=$((current_code + 1))

sed -i '' "s/^lumen.versionName=.*/lumen.versionName=$version/; s/^lumen.versionCode=.*/lumen.versionCode=$next_code/" "$props"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
(cd "$root" && ./gradlew -q clean assembleSideloadRelease)

apk="$root/app/build/outputs/apk/sideload/release/app-sideload-release.apk"
build_tools="$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/* | tail -1)"
signer="$("$build_tools/apksigner" verify --print-certs "$apk" | grep 'certificate DN')"
if [[ "$signer" == *"Android Debug"* ]]; then
    echo "Refusing to publish a debug-signed build" >&2
    exit 1
fi

out="$root/dist/lumen-$version-sideload.apk"
mkdir -p "$root/dist"
cp "$apk" "$out"
sha="$(shasum -a 256 "$out" | cut -d' ' -f1)"

notes_args=(--notes "SHA-256: \`$sha\`")
if [[ -n "$notes" ]]; then
    notes_args=(--notes "$(cat "$notes")"$'\n\n'"SHA-256: \`$sha\`")
fi

gh release create "v$version" "$out" --repo "$repo" --title "Lumen $version" "${notes_args[@]}"
echo "Published v$version (versionCode $next_code). Commit gradle.properties to record the bump."
