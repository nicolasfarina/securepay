#!/usr/bin/env bash
set -Eeuo pipefail

# This script runs only against a newly created, disposable Compose project.
# It is not a generic URL scanner and accepts no arbitrary target parameter.
if [[ "${CI:-}" != "true" && "${SECUREPAY_ALLOW_DAST_LOCAL:-}" != "yes" ]]; then
  echo "DAST requires CI=true or SECUREPAY_ALLOW_DAST_LOCAL=yes" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
image="ghcr.io/zaproxy/zaproxy:stable@sha256:030d3df57735dacbb83464854758e674dd9ad01d79d12a129a8e58b1fd384238"
project="securepay-dast-${GITHUB_RUN_ID:-manual}-${RANDOM}"
project="${project,,}"
compose=(docker compose -p "$project" -f compose.dast.yaml)
reports="$PWD/target/zap-reports"
export DAST_IMAGE_TAG="${GITHUB_RUN_ID:-manual}-${RANDOM}"
export SECUREPAY_DATABASE_PASSWORD
export SECUREPAY_DEMO_PASSWORD
export SECUREPAY_JWT_SECRET

SECUREPAY_DATABASE_PASSWORD="$(openssl rand -hex 24)"
SECUREPAY_DEMO_PASSWORD="$(openssl rand -hex 24)"
SECUREPAY_JWT_SECRET="$(openssl rand -hex 48)"

cleanup() {
  local result=$?
  trap - EXIT
  if [[ -n "${zap_log:-}" ]]; then rm -f "$zap_log"; fi
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  unset SECUREPAY_DATABASE_PASSWORD SECUREPAY_DEMO_PASSWORD SECUREPAY_JWT_SECRET
  exit "$result"
}
trap cleanup EXIT

mkdir -p "$reports"
# ZAP's non-root uid must be able to save reports in this disposable runner directory.
chmod 0777 "$reports"

echo "Building an isolated disposable Compose stack (no published ports)"
"${compose[@]}" up --build -d --wait --wait-timeout 240
network="${project}_dast"
docker network inspect "$network" >/dev/null

# The smoke script prints ONLY the short-lived JWT; it never writes token files.
token="$(docker run --rm --network "$network" \
  -e DAST_DEMO_PASSWORD="$SECUREPAY_DEMO_PASSWORD" \
  -v "$PWD/scripts/dast-smoke.py:/zap/dast-smoke.py:ro" \
  --entrypoint python3 "$image" /zap/dast-smoke.py)"
if [[ -z "$token" || "$token" != *.*.* ]]; then
  echo "DAST preflight did not return a JWT" >&2
  exit 1
fi
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  printf '::add-mask::%s\n' "$token"
fi
printf '%s\n' '{"target":"isolated-compose-only","anonymous_payment":401,"authenticated_missing_payment":404,"readiness":200,"openapi":200,"methods_scanned":"GET-only; passive ZAP"}' > "$reports/coverage.json"

echo "Running ZAP passive baseline against the isolated API; no activeScan job"
zap_log="$(mktemp)"
set +e
docker run --rm --network "$network" \
  -e "ZAP_AUTH_HEADER_VALUE=Bearer $token" \
  -e ZAP_AUTH_HEADER_SITE=api:8080 \
  -v "$PWD/security/zap/plan.yaml:/zap/wrk/plan.yaml:ro" \
  -v "$reports:/zap/wrk/reports:rw" \
  --entrypoint zap.sh "$image" -cmd -autorun /zap/wrk/plan.yaml 2>&1 | tee "$zap_log"
scan_status=${PIPESTATUS[0]}
set -e

# ZAP requestor mismatches are warnings by default; enforce them as failures
# independently of the Low-severity vulnerability warning tolerance.
if grep -Eq "Difference in response code values|Unrecognised parameter for job|Job spider error accessing URL" "$zap_log"; then
  echo "FAIL: ZAP path coverage or plan validation did not match expectations" >&2
  exit 1
fi
rm -f "$zap_log"
zap_log=""

# Never upload a report that accidentally contains a live bearer or demo password.
for artifact in "$reports/zap-baseline.html" "$reports/zap-baseline.json"; do
  if [[ -f "$artifact" ]] && (grep -Fq -- "$token" "$artifact" || grep -Fq -- "$SECUREPAY_DEMO_PASSWORD" "$artifact"); then
    rm -f "$reports/zap-baseline.html" "$reports/zap-baseline.json"
    echo "DAST report contained a transient credential; artifacts removed" >&2
    exit 1
  fi
done
unset token

# A no-report scan is not a passing scan. Check well-formed JSON and scoped target.
test -s "$reports/zap-baseline.html"
test -s "$reports/zap-baseline.json"
python3 - "$reports/zap-baseline.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    report = json.load(handle)
sites = report.get("site", [])
if not isinstance(sites, list):
    sites = [sites]
if not any("api:8080" in str(site.get("@name", "")) for site in sites):
    raise SystemExit("ZAP report did not cover the isolated API")
print(f"ZAP report includes {len(sites)} scoped site(s)")
PY

# ZAP Automation exit codes: 0=clean, 2=low risk warnings (review as artifact);
# 1=scan error or medium/high finding -> fail. Any other error also fails.
case "$scan_status" in
  0) echo "PASS: ZAP passive scan completed without findings above threshold" ;;
  2) echo "WARN: ZAP low-risk findings recorded for review (threshold: medium/high)" ;;
  *) echo "FAIL: ZAP returned status $scan_status" >&2; exit 1 ;;
esac
