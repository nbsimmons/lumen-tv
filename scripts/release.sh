#!/usr/bin/env bash
# Bumps the version, tags it and pushes; GitHub Actions then builds, signs and publishes the release.
# Usage: scripts/release.sh 0.2.0 [notes.md] [--local]   (--local builds and publishes from this Mac instead)
set -euo pipefail

version="${1:?Usage: scripts/release.sh <version> [notes-file] [--local]}"
notes=""
local_build=false
for arg in "${@:2}"; do
    if [[ "$arg" == "--local" ]]; then local_build=true; else notes="$arg"; fi
done
root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/gradle.properties"

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3" >&2; exit 1; }
command -v gh >/dev/null || { echo "Install the GitHub CLI: brew install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Sign in first: gh auth login" >&2; exit 1; }
[[ -z "$(git -C "$root" status --porcelain)" ]] || { echo "Commit or stash your changes first" >&2; exit 1; }

current_code="$(grep '^lumen.versionCode=' "$props" | cut -d= -f2)"
next_code=$((current_code + 1))
sed -i '' "s/^lumen.versionName=.*/lumen.versionName=$version/; s/^lumen.versionCode=.*/lumen.versionCode=$next_code/" "$props"
git -C "$root" add gradle.properties
git -C "$root" commit -q -m "Bump version to $version"

if $local_build; then
    export LUMEN_SIGNING="${LUMEN_SIGNING:-$HOME/.lumen/signing.properties}"
    [[ -f "$LUMEN_SIGNING" ]] || { echo "Missing signing config at $LUMEN_SIGNING" >&2; exit 1; }
    export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
    git -C "$root" push -q origin HEAD
    "$root/scripts/package-release.sh" "$notes"
else
    message="Lumen $version"
    [[ -n "$notes" ]] && message="$(cat "$notes")"
    git -C "$root" tag -a "v$version" -m "$message"
    git -C "$root" push -q origin HEAD "v$version"
    echo "Pushed v$version (versionCode $next_code). GitHub Actions is building the release:"
    echo "  https://github.com/$(grep '^lumen.updateRepo=' "$props" | cut -d= -f2-)/actions"
fi
