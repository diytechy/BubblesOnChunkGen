#!/usr/bin/env bash
# Packages the Bedrock behavior + resource packs into a single .mcaddon.
# The pack version is stamped from gradle.properties so it tracks the Java artifacts.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="$(sed -n 's/^version=//p' ../gradle.properties | tr -d '[:space:]')"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Could not read a x.y.z version from gradle.properties (got '$VERSION')" >&2
    exit 1
fi
IFS=. read -r MAJOR MINOR PATCH <<<"$VERSION"
VERSION_JSON="[$MAJOR,$MINOR,$PATCH]"

for tool in jq zip node; do
    command -v "$tool" >/dev/null || { echo "missing required tool: $tool" >&2; exit 1; }
done

node --check BubblesOnChunkGen_BP/scripts/main.js
node --check BubblesOnChunkGen_BP/scripts/core.js
for f in BubblesOnChunkGen_BP/manifest.json BubblesOnChunkGen_BP/blocks/*.json \
         BubblesOnChunkGen_RP/manifest.json BubblesOnChunkGen_RP/models/blocks/*.json; do
    jq -e . "$f" >/dev/null
done

OUT=build
STAGE="$OUT/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -R BubblesOnChunkGen_BP BubblesOnChunkGen_RP "$STAGE/"

for m in "$STAGE"/*/manifest.json; do
    jq --argjson v "$VERSION_JSON" '
        .header.version = $v
        | .modules |= map(.version = $v)
        | .dependencies |= (if . then map(if has("uuid") then .version = $v else . end) else . end)
    ' "$m" > "$m.tmp" && mv "$m.tmp" "$m"
done

ADDON="BubblesOnChunkGen-Bedrock-$VERSION.mcaddon"
rm -f "$OUT/$ADDON"
(cd "$STAGE" && zip -qr -X "../$ADDON" BubblesOnChunkGen_BP BubblesOnChunkGen_RP)
rm -rf "$STAGE"

echo "built $OUT/$ADDON"
