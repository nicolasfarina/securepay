"""Fixed-target, explicit-request passive DAST for the disposable Compose stack."""
import json
from pathlib import Path
import re
import secrets
import subprocess
import time
import uuid

import requests

TARGET = "http://api:8080"
ZAP = "http://127.0.0.1:8090"


def target_url(path):
    # No caller-supplied hosts, schemes, queries, fragments, or redirects.
    if not re.fullmatch(r"/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]*", path):
        raise ValueError("Only local API paths are permitted")
    return TARGET + path


def sanitized_alerts(alerts):
    fields = ("pluginId", "alert", "risk", "confidence", "url", "description", "solution", "reference")
    return [{key: alert.get(key, "") for key in fields} for alert in alerts]


def run():
    output = Path("/zap/wrk/reports")
    output.mkdir(parents=True, exist_ok=True)
    key = secrets.token_hex(24)
    control = requests.Session()
    control.trust_env = False
    traffic = requests.Session()
    traffic.trust_env = False
    traffic.proxies = {"http": ZAP}
    checks = []
    result = {"target": TARGET, "mode": "passive-explicit-requests", "checks": checks,
              "alerts": [], "completed": False}

    def api(component, kind, operation, **params):
        response = control.get(f"{ZAP}/JSON/{component}/{kind}/{operation}/",
                               params={"apikey": key, **params}, timeout=15)
        if response.status_code != 200:
            raise RuntimeError(f"ZAP API {component}/{operation}: HTTP {response.status_code}")
        data = response.json()
        if "code" in data:
            raise RuntimeError(f"ZAP API rejected {component}/{operation}: {data['code']}")
        return data

    def request(label, method, path, expected, token=None, **kwargs):
        headers = kwargs.pop("headers", {})
        if token:
            headers["Authorization"] = "Bearer " + token
        response = traffic.request(method, target_url(path), headers=headers,
                                   allow_redirects=False, timeout=20, **kwargs)
        checks.append({"name": label, "method": method, "path": path,
                       "expected": expected, "actual": response.status_code})
        if response.status_code != expected:
            raise RuntimeError(f"{label}: expected {expected}, got {response.status_code}")
        return response

    def login(user):
        return request("login " + user, "POST", "/auth/login", 200,
                       json={"username": user, "password": "dast-local-only"}).json()["accessToken"]

    with (output / "zap.log").open("w") as log:
        process = subprocess.Popen([
            "zap.sh", "-daemon", "-host", "127.0.0.1", "-port", "8090",
            "-config", "api.key=" + key,
            "-config", "autoupdate.checkOnStart=false",
            "-config", "autoupdate.downloadNewRelease=false"], stdout=log, stderr=log)
        try:
            for _ in range(90):
                try:
                    result["zapVersion"] = api("core", "view", "version")["version"]
                    break
                except requests.RequestException:
                    if process.poll() is not None:
                        raise RuntimeError("ZAP exited during startup")
                    time.sleep(1)
            else:
                raise RuntimeError("ZAP startup timed out")
            api("context", "action", "newContext", contextName="local")
            api("context", "action", "includeInContext", contextName="local",
                regex=r"http://api:8080/.*")
            api("context", "action", "setContextInScope", contextName="local", booleanInScope="true")
            api("pscan", "action", "setScanOnlyInScope", onlyInScope="true")
            api("core", "action", "setMode", mode="safe")
            request("readiness", "GET", "/actuator/health/readiness", 200)
            request("local OpenAPI document (no import/crawl)", "GET", "/v3/api-docs", 200)
            request("anonymous payments", "GET", "/api/admin/payments", 401)
            request("anonymous metrics", "GET", "/actuator/metrics", 401)
            request("invalid bearer", "GET", "/api/admin/payments", 401, token="invalid")
            request("invalid login", "POST", "/auth/login", 401,
                    json={"username": "customer-123", "password": "incorrect"})
            customer = login("customer-123")
            other = login("customer-789")
            admin = login("admin-123")
            request("customer denied admin payments", "GET", "/api/admin/payments", 403, customer)
            request("customer denied metrics", "GET", "/actuator/metrics", 403, customer)
            request("admin payments", "GET", "/api/admin/payments", 200, admin)
            payment = request("synthetic payment", "POST", "/api/payments", 201, customer,
                              headers={"Idempotency-Key": "dast-" + str(uuid.uuid4())},
                              json={"merchantId": "dast-merchant", "amount": "1.00", "currency": "USD"}).json()
            payment_id = str(uuid.UUID(payment["id"]))
            request("own payment", "GET", "/api/payments/" + payment_id, 200, customer)
            request("other customer's payment hidden", "GET", "/api/payments/" + payment_id, 404, other)
            request("logout", "POST", "/auth/logout", 204, customer)
            request("revoked bearer", "GET", "/api/payments/" + payment_id, 401, customer)
            for _ in range(120):
                if int(api("pscan", "view", "recordsToScan")["recordsToScan"]) == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("Passive scan queue did not drain")
            alerts = api("core", "view", "alerts", baseurl=TARGET, start=0, count=10000)["alerts"]
            result["alerts"] = sanitized_alerts(alerts)
            result["completed"] = True
            result["gatePassed"] = not any(a["risk"] in ("Medium", "High") for a in alerts)
            return 0 if result["gatePassed"] else 1
        except Exception as error:
            # Do not serialize HTTP bodies, request headers, tokens, or credentials.
            detail = "request failed" if isinstance(error, requests.RequestException) else str(error)
            result["error"] = type(error).__name__ + ": " + detail
            return 1
        finally:
            (output / "summary.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
            print(json.dumps({"completed": result["completed"], "checks": len(checks),
                              "alerts": len(result["alerts"]), "error": result.get("error")}))
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()


if __name__ == "__main__":
    raise SystemExit(run())
