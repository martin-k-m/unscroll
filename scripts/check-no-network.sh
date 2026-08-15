#!/usr/bin/env bash
# Fails if unscroll could reach the network.
#
# The README says nothing leaves the phone. That claim rests entirely on the app
# not holding android.permission.INTERNET, because without it the platform
# refuses the socket no matter what the code asks for. Nothing asserted it, so
# the claim was a promise about a file anyone could edit.
#
# Checking the source manifest alone is not enough. Manifest merging lets any
# library contribute a <uses-permission>, so a dependency added later can grant
# INTERNET without a line of this project's XML changing. The merged manifest is
# the one that ships, so that is the one worth checking, and CI passes it in.
#
# Usage:
#   scripts/check-no-network.sh                     # source manifest and code
#   scripts/check-no-network.sh --merged <path>     # also the merged manifest
set -euo pipefail

cd "$(dirname "$0")/.."

fail() { echo "FAIL: $*" >&2; exit 1; }

merged=""
while [ $# -gt 0 ]; do
    case "$1" in
        --merged)
            [ $# -ge 2 ] || fail "--merged needs a path"
            merged="$2"
            shift 2
            ;;
        *) fail "unknown argument: $1" ;;
    esac
done

source_manifest="app/src/main/AndroidManifest.xml"
[ -f "$source_manifest" ] || fail "no manifest at $source_manifest"

if grep -q "android.permission.INTERNET" "$source_manifest"; then
    fail "$source_manifest declares INTERNET, so the no-network claim is false"
fi
echo "ok: $source_manifest does not declare INTERNET"

# Network use without the permission throws at runtime rather than silently
# working, so this is about catching the intent early, not about the guarantee.
# It is deliberately a small list of the ways a request actually gets made.
offenders=$(grep -rnE \
    'java\.net\.(URL|Socket|HttpURLConnection)|HttpURLConnection|okhttp3|retrofit2|Volley|java\.net\.URLConnection|WebView' \
    --include="*.kt" --include="*.java" app/src/main 2>/dev/null || true)
if [ -n "$offenders" ]; then
    echo "$offenders" >&2
    fail "source references a networking API"
fi
echo "ok: no networking API referenced in app/src/main"

if [ -n "$merged" ]; then
    # Required, not optional. A missing file here means the build layout moved
    # and this check silently stopped checking, which is worse than failing.
    [ -f "$merged" ] || fail "merged manifest not found at $merged"
    if grep -q "android.permission.INTERNET" "$merged"; then
        fail "the merged manifest grants INTERNET, so a dependency contributed it"
    fi
    echo "ok: $merged does not grant INTERNET"
else
    echo "note: no merged manifest checked, so this run cannot see a permission"
    echo "      a dependency contributes. CI passes --merged for that."
fi
