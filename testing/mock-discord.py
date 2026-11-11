#!/usr/bin/env python3
"""mock-discord.py <port> <logfile>: a stand-in for Discord's webhook API on 127.0.0.1 (KaboofDiscord tests).
Every request is appended to <logfile> as one JSON line (method, path, body); POSTs answer like Discord with ?wait=true
(a message id), PATCH and DELETE answer 200/204. Never talks to the internet."""
import json, sys, itertools
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT, LOG = int(sys.argv[1]), sys.argv[2]
ids = itertools.count(1000)

class H(BaseHTTPRequestHandler):
    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        try:
            return json.loads(raw) if raw else None
        except ValueError:
            return raw.decode("utf-8", "replace")

    def _log(self, body):
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"method": self.command, "path": self.path, "body": body}, ensure_ascii=False) + "\n")

    def _reply(self, code, obj=None):
        data = json.dumps(obj).encode() if obj is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        self._log(self._body())
        self._reply(200, {"id": str(next(ids))})

    def do_PATCH(self):
        self._log(self._body())
        self._reply(200, {"id": self.path.rstrip("/").split("/")[-1].split("?")[0]})

    def do_DELETE(self):
        self._log(None)
        self._reply(204)

    def do_GET(self):
        self._log(None)
        self._reply(200, {"id": "1"})

    def log_message(self, *args):
        pass

ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
