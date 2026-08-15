#!/usr/bin/env bash
# Fails if the app could reach the network. Without INTERNET the platform refuses
# the socket, so the permission is the whole of the README's privacy claim.
#
# The source manifest is not enough on its own: manifest merging lets a
# dependency contribute a permission, and the merged manifest is what ships. CI
# passes --merged for that.
#
# Usage: check-no-network.sh [--merged <path>]
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "FAIL: $*" >&2; exit 1; }

merged=""
case "${1-}" in
    --merged) merged="${2-}"; [ -n "$merged" ] || fail "--merged needs a path" ;;
    "") ;;
    *) fail "unknown argument: $1" ;;
esac

check() {
    [ -f "$1" ] || fail "no manifest at $1"
    ! grep -q "android.permission.INTERNET" "$1" || fail "$1 grants INTERNET"
    echo "ok: $1 does not grant INTERNET"
}

check app/src/main/AndroidManifest.xml

# Networking without the permission throws rather than silently working, so this
# catches the intent early. It is not what makes the claim true.
if grep -rnE 'java\.net\.(URL|Socket|URLConnection|HttpURLConnection)|okhttp3|retrofit2|Volley|WebView' \
        --include="*.kt" --include="*.java" app/src/main; then
    fail "source references a networking API"
fi
echo "ok: no networking API referenced in app/src/main"

if [ -n "$merged" ]; then
    check "$merged"
else
    echo "note: no merged manifest checked, so a dependency's permission would be missed"
fi
