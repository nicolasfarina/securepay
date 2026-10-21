#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
api_port="${SECUREPAY_API_PORT:-8080}"
project_name=""

usage() {
  cat <<'EOF'
Usage: ./scripts/run-local.sh [--api-port PORT] [--project-name NAME]

Build and start SecurePay with PostgreSQL, then wait until both are healthy.
The database volume is preserved when stopped with the printed Compose command.
EOF
}

while (($#)); do
  case "$1" in
    --api-port)
      (($# >= 2)) || { echo "Missing value for --api-port" >&2; exit 2; }
      api_port="$2"
      shift 2
      ;;
    --project-name)
      (($# >= 2)) || { echo "Missing value for --project-name" >&2; exit 2; }
      project_name="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ "$api_port" =~ ^[0-9]+$ ]] && ((api_port >= 1 && api_port <= 65535)) || {
  echo "API port must be an integer from 1 to 65535." >&2
  exit 2
}
[[ -z "$project_name" || "$project_name" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || {
  echo "Project name must start with a lowercase letter or digit and contain only lowercase letters, digits, underscores, or hyphens." >&2
  exit 2
}

command -v docker >/dev/null 2>&1 || { echo "Docker is required. Start Docker Desktop and try again." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker Engine is not running. Start Docker Desktop and try again." >&2; exit 1; }

# Compose intentionally requires explicit local-only credentials. Save generated
# defaults outside version control so repeated starts keep the same PostgreSQL
# password and JWT signing key.
credentials_file="$repo_root/.env.securepay-local"
if [[ ! -f "$credentials_file" ]]; then
  umask 077
  database_password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  demo_password="$(od -An -N18 -tx1 /dev/urandom | tr -d ' \n')"
  jwt_secret="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  printf 'SECUREPAY_DATABASE_PASSWORD=%s\nSECUREPAY_DEMO_PASSWORD=%s\nSECUREPAY_JWT_SECRET=%s\n' \
    "$database_password" "$demo_password" "$jwt_secret" > "$credentials_file"
fi

export SECUREPAY_API_PORT="$api_port"

compose=(docker compose --env-file "$credentials_file")
if [[ -n "$project_name" ]]; then
  compose+=(--project-name "$project_name")
fi

if ! cd "$repo_root" || ! "${compose[@]}" up --build --detach --wait --wait-timeout 120; then
  "${compose[@]}" logs --tail 60 api postgres || true
  echo "SecurePay did not become healthy. Check port $api_port and the service logs above." >&2
  exit 1
fi

echo "SecurePay and PostgreSQL are healthy."
echo "API:       http://127.0.0.1:$api_port"
echo "Swagger:   http://127.0.0.1:$api_port/swagger-ui/index.html"
echo "Stop and preserve data: ${compose[*]} down"
