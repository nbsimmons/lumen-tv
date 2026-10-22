#!/usr/bin/env bash
# Builds the signed sideload APKs for the version in gradle.properties and publishes them as a GitHub release.
set -euo pipefail

notes="${1:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/gradle.properties"
version="$(grep '^lumen.versionName=' "$props" | cut -d= -f2)"
repo="$(grep '^lumen.updateRepo=' "$props" | cut -d= -f2-)"
[[ -n "$repo" ]] || { echo "Set lumen.updateRepo=owner/name in gradle.properties" >&2; exit 1; }

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

(cd "$root" && ./gradlew -q clean assembleSideloadModernRelease assembleSideloadLegacyRelease)

build_tools="$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/* | sort -V | tail -1)"
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

dist="$root/dist"
mkdir -p "$dist"
out="$dist/lumen-$version-sideload.apk"
legacy_out="$dist/lumen-$version-legacy.apk"
cp "$modern_apk" "$out"
cp "$legacy_apk" "$legacy_out"
cp "$modern_apk" "$dist/lumen-sideload.apk"
cp "$legacy_apk" "$dist/lumen-legacy.apk"

checksums="SHA-256 (Fire OS 6+ / Android TV): \`$(sha256 "$out")\`"$'\n'"SHA-256 (Fire OS 5 legacy): \`$(sha256 "$legacy_out")\`"
body="$checksums"
if [[ -n "$notes" && -s "$notes" ]]; then
    body="$(cat "$notes")"$'\n\n'"$checksums"
fi

profiles=()
while IFS=$'\t' read -r min path; do
    [[ -n "$path" ]] || continue
    name="lumen-$version-sideload-api$min.dm"
    cp "$modern_dir/$path" "$dist/$name"
    profiles+=("$dist/$name")
done < <(python3 - "$modern_dir/output-metadata.json" <<'PY'
import json, sys
for entry in json.load(open(sys.argv[1])).get("baselineProfiles", []):
    print("%s\t%s" % (entry["minApi"], entry["baselineProfiles"][0]))
PY
)

if [[ -n "${LUMEN_DRY_RUN:-}" ]]; then
    echo "Dry run: would publish v$version with:"
    printf '  %s\n' "$out" "$legacy_out" "$dist/lumen-sideload.apk" "$dist/lumen-legacy.apk" "${profiles[@]}"
    printf '%s\n' "$body"
    exit 0
fi

gh release create "v$version" "$out" "$legacy_out" "$dist/lumen-sideload.apk" "$dist/lumen-legacy.apk" "${profiles[@]}" \
    --repo "$repo" --title "Lumen $version" --notes "$body"
echo "Published v$version: https://github.com/$repo/releases/tag/v$version"
