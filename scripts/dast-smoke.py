#!/usr/bin/env python3
"""Read-only HTTP preflight on the isolated Compose network.

Writes only the short-lived JWT to stdout for capture by the runner. Never write
credentials or tokens to logs or the uploaded artifacts.
"""
import json
import os
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://api:8080"
TEST_PAYMENT = "/api/payments/00000000-0000-0000-0000-000000000000"


def request(path, *, payload=None, bearer=None):
    headers = {}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    req = Request(BASE + path, data=body, headers=headers,
                  method="POST" if payload is not None else "GET")
    try:
        with urlopen(req, timeout=15) as response:
            return response.status, response.read()
    except HTTPError as exc:
        return exc.code, exc.read()


def assert_status(expected, actual, probe):
    if actual != expected:
        raise RuntimeError(f"{probe}: expected HTTP {expected}, received HTTP {actual}")


def main():
    status, _ = request("/actuator/health/readiness")
    assert_status(200, status, "readiness")
    status, _ = request("/v3/api-docs")
    assert_status(200, status, "OpenAPI")
    status, _ = request(TEST_PAYMENT)
    assert_status(401, status, "anonymous payment")
    status, body = request("/auth/login", payload={
        "username": "customer-123",
        "password": os.environ["DAST_DEMO_PASSWORD"],
    })
    assert_status(200, status, "login")
    token = json.loads(body)["accessToken"]
    if not isinstance(token, str) or token.count(".") != 2:
        raise RuntimeError("login did not issue the expected bearer JWT")
    status, _ = request(TEST_PAYMENT, bearer=token)
    assert_status(404, status, "authenticated missing payment")
    sys.stdout.write(token)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Never print HTTP response bodies; they might contain credentials.
        print(f"DAST preflight failed: {exc}", file=sys.stderr)
        sys.exit(1)
