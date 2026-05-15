#!/usr/bin/env python3
from __future__ import annotations

import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PROXY_PREFIX = "/__ibus_proxy__/"
ALLOWED_HOSTS = {
    "app.ibuscloud.com",
    "apptest.ibuscloud.com",
    "apph5.ibuscloud.com",
    "apph5-test.ibuscloud.com",
}
HOP_BY_HOP_HEADERS = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
}


class RealtimeBusHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def _normalized_path(self) -> str:
        return urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)

    def _redirect_location(self) -> str:
        parsed = urllib.parse.urlsplit(self.path)
        location = "/index.html"
        if parsed.query:
            location += "?" + parsed.query
        return location

    def do_GET(self) -> None:
        if self._normalized_path() in {
            "/",
            "",
            "/实时公交网页",
            "/实时公交网页/",
            "/ibuscloud_realtime/index.html",
            "/ibuscloud_realtime/",
        }:
            self._redirect_to_app()
            return
        if self.path.startswith(PROXY_PREFIX):
            self._proxy_request()
            return
        super().do_GET()

    def do_HEAD(self) -> None:
        if self._normalized_path() in {
            "/",
            "",
            "/实时公交网页",
            "/实时公交网页/",
            "/ibuscloud_realtime/index.html",
            "/ibuscloud_realtime/",
        }:
            self.send_response(302)
            self.send_header("Location", self._redirect_location())
            self.end_headers()
            return
        if self.path.startswith(PROXY_PREFIX):
            self._proxy_request(send_body=False)
            return
        super().do_HEAD()

    def do_POST(self) -> None:
        if self.path.startswith(PROXY_PREFIX):
            self._proxy_request()
            return
        self.send_error(405, "Method not allowed")

    def log_message(self, format: str, *args) -> None:
        sys.stdout.write("%s - - [%s] %s\n" % (self.client_address[0], self.log_date_time_string(), format % args))

    def _redirect_to_app(self) -> None:
        self.send_response(302)
        self.send_header("Location", self._redirect_location())
        self.end_headers()

    def _proxy_request(self, send_body: bool = True) -> None:
        parsed = urllib.parse.urlsplit(self.path)
        remote = parsed.path[len(PROXY_PREFIX):]
        host, _, remote_path = remote.partition("/")
        if host not in ALLOWED_HOSTS:
            self.send_error(403, "Proxy host not allowed")
            return
        target = f"https://{host}/{remote_path}"
        if parsed.query:
            target += "?" + parsed.query
        body = None
        if self.command in {"POST", "PUT", "PATCH"}:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length) if length > 0 else None
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in HOP_BY_HOP_HEADERS and key.lower() != "host"
        }
        headers.setdefault("User-Agent", "Mozilla/5.0 Codex Local Proxy")
        request = urllib.request.Request(target, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                self.send_response(response.status)
                for key, value in response.headers.items():
                    lower_key = key.lower()
                    if lower_key in HOP_BY_HOP_HEADERS:
                        continue
                    if lower_key == "content-length" and not send_body:
                        continue
                    self.send_header(key, value)
                self.end_headers()
                if send_body:
                    self.wfile.write(response.read())
        except urllib.error.HTTPError as err:
            self.send_response(err.code)
            for key, value in err.headers.items():
                if key.lower() not in HOP_BY_HOP_HEADERS:
                    self.send_header(key, value)
            self.end_headers()
            if send_body:
                self.wfile.write(err.read())
        except Exception as err:  # noqa: BLE001
            self.send_error(502, f"Proxy error: {err}")


def main() -> None:
    host = os.environ.get("IBUS_SERVER_HOST", "127.0.0.1")
    port = int(os.environ.get("IBUS_SERVER_PORT", "8000"))
    server = ThreadingHTTPServer((host, port), RealtimeBusHandler)
    print(f"Serving {ROOT} at http://{host}:{port}/")
    print(f"Open: http://{host}:{port}/")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
