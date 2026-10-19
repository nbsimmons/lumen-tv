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
(cd "$root" && ./gradlew -q clean assembleSideloadModernRelease assembleSideloadLegacyRelease)

build_tools="$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/* | tail -1)"
modern_dir="$root/app/build/outputs/apk/sideloadModern/release"
legacy_dir="$root/app/build/outputs/apk/sideloadLegacy/release"
modern_apk="$modern_dir/app-sideload-modern-release.apk"
legacy_apk="$legacy_dir/app-sideload-legacy-release.apk"
for apk in "$modern_apk" "$legacy_apk"; do
    signer="$("$build_tools/apksigner" verify --print-certs "$apk" | grep 'certificate DN')"
    if [[ "$signer" == *"Android Debug"* ]]; then
        echo "Refusing to publish a debug-signed build" >&2
        exit 1
    fi
done

mkdir -p "$root/dist"
out="$root/dist/lumen-$version-sideload.apk"
legacy_out="$root/dist/lumen-$version-legacy.apk"
cp "$modern_apk" "$out"
cp "$legacy_apk" "$legacy_out"
cp "$modern_apk" "$root/dist/lumen-sideload.apk"
cp "$legacy_apk" "$root/dist/lumen-legacy.apk"
sha="$(shasum -a 256 "$out" | cut -d' ' -f1)"
legacy_sha="$(shasum -a 256 "$legacy_out" | cut -d' ' -f1)"

checksums="SHA-256 (Fire OS 6+ / Android TV): \`$sha\`"$'\n'"SHA-256 (Fire OS 5 legacy): \`$legacy_sha\`"
notes_args=(--notes "$checksums")
if [[ -n "$notes" ]]; then
    notes_args=(--notes "$(cat "$notes")"$'\n\n'"$checksums")
fi

profiles=()
while IFS=$'\t' read -r min path; do
    [[ -n "$path" ]] || continue
    name="lumen-$version-sideload-api$min.dm"
    cp "$modern_dir/$path" "$root/dist/$name"
    profiles+=("$root/dist/$name")
done < <(python3 - "$modern_dir/output-metadata.json" <<'PY'
import json, sys
for entry in json.load(open(sys.argv[1])).get("baselineProfiles", []):
    print("%s\t%s" % (entry["minApi"], entry["baselineProfiles"][0]))
PY
)

gh release create "v$version" "$out" "$legacy_out" "$root/dist/lumen-sideload.apk" "$root/dist/lumen-legacy.apk" "${profiles[@]}" \
    --repo "$repo" --title "Lumen $version" "${notes_args[@]}"
echo "Stable links: https://github.com/$repo/releases/latest/download/lumen-sideload.apk (Fire OS 6+ / Android TV)"
echo "              https://github.com/$repo/releases/latest/download/lumen-legacy.apk (Fire OS 5)"
echo "Published v$version (versionCode $next_code). Commit gradle.properties to record the bump."
