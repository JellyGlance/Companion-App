#!/usr/bin/env bash
# Saves real responses from your JellyGlance instance into tools/fixtures/ so the mock
# server can replay them (node tools/mock-server.js 7788). The API key is read without
# echoing and is never written to disk; only the responses are saved.
#
#   bash tools/capture.sh https://jellyglance.example.com
set -euo pipefail

BASE="${1:-}"
if [ -z "$BASE" ]; then read -rp "JellyGlance URL: " BASE; fi
BASE="${BASE%/}"
read -rsp "API key (hidden): " KEY; echo

OUT="$(cd "$(dirname "$0")" && pwd)/fixtures"
mkdir -p "$OUT"
printf '%s\n' "$BASE" > "$OUT/_base_url"

routes=(
  /api/jellyfin/status
  /api/widgets/nowplaying /api/widgets/sessions /api/widgets/viewers /api/widgets/watch
  /api/widgets/activity /api/widgets/statistics /api/widgets/users /api/widgets/devices
  /api/widgets/catalog /api/widgets/recent /api/widgets/libraries /api/widgets/storage
  /api/widgets/downloads /api/widgets/stalled /api/widgets/stitched /api/widgets/cleanuparr
  /api/widgets/requests /api/widgets/issues /api/widgets/calendar /api/widgets/today
  /api/widgets/health /api/widgets/transcodes /api/widgets/maintainerr /api/widgets/digest
  /api/widgets/repair /api/widgets/invites /api/widgets/autobrr /api/widgets/automation
  /api/widgets/webhooks /api/widgets/jobs /api/widgets/jellyfin-jobs /api/widgets/newsletter
  /api/widgets/backup /api/widgets/homepage
  /proxy/getSessions /api/requests /api/integrations/calendar
)

for r in "${routes[@]}"; do
  file="$OUT/$(echo "$r" | sed 's#^/##; s#/#__#g').json"
  code=$(curl -s -o "$file" -w '%{http_code}' -H "x-api-token: $KEY" -H 'Accept: application/json' --max-time 30 "$BASE$r" || echo 000)
  printf '%-32s %s\n' "$r" "$code"
  [ "$code" = "200" ] || rm -f "$file"
done

# A few item-glance samples from recently added titles.
if [ -f "$OUT/api__widgets__recent.json" ]; then
  for id in $(grep -o '"id":"[^"]*"' "$OUT/api__widgets__recent.json" | head -4 | cut -d'"' -f4); do
    curl -s -o "$OUT/item-glance__$id.json" -H "x-api-token: $KEY" --max-time 30 "$BASE/api/item-glance/$id" || true
  done
fi
unset KEY
echo "Saved to $OUT"
