#!/usr/bin/env python3
"""Verify the deployed site, same-origin API proxy and optional administrator session."""
import argparse
import http.cookiejar
import json
import pathlib
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("base_url")
parser.add_argument("--admin-password-file")
args = parser.parse_args()
base = args.base_url.rstrip("/")
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(path, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, headers={"Content-Type": "application/json", **(headers or {})})
    with opener.open(req, timeout=15) as response:
        return response.read().decode()

assert json.loads(request("/health"))["status"] == "UP"
page = request("/")
assert 'dir="rtl"' in page and "قطعه‌یار" in page
assert "رمز" in request("/login")
assert isinstance(json.loads(request("/api/lookups")), dict)
assert isinstance(json.loads(request("/api/public/news")), list)
assert isinstance(json.loads(request("/api/products")), list)

if args.admin_password_file:
    password = pathlib.Path(args.admin_password_file).read_text().strip()
    json.loads(request("/api/auth/login", {"identifier": "manager0", "password": password}))
    me = json.loads(request("/api/auth/me"))
    assert me.get("root") is True and "SUPER_ADMIN" in me.get("roles", [])
    assert isinstance(json.loads(request("/api/admin/users")), list)
    csrf = json.loads(request("/api/auth/csrf"))
    request("/api/auth/logout", {}, {csrf["headerName"]: csrf["token"]})
    try:
        request("/api/auth/me")
    except urllib.error.HTTPError as error:
        assert error.code in (401, 403)
    else:
        raise AssertionError("Logout did not end the administrator session")
    print("Administrator login, authorization, CSRF and logout verified.")
print("Website, Persian RTL layout, database health and same-origin API verified.")
