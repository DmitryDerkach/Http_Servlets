"""2026: records real HTTP exchanges for the "under the hood" page (site/index.html).

Needs the built application (./run.sh web, port 8080; the script recreates the Tomcat container itself)
and the hand-made server (server.HttpServerRunner, port 9000) running. Sends every request over a plain TCP socket and
saves the exact bytes both ways, plus what the servlets printed to the Tomcat log, to site/captures.js.
The GitHub Pages job in .github/workflows/ci.yml runs it on every deploy, so the page always shows
the answers of the current code.

Usage: python3 site/capture.py [tomcat_port] [server_port]
"""
import datetime
import json
import os
import pathlib
import socket
import subprocess
import sys
import urllib.parse

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
TOMCAT_PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
SERVER_PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 9000
AGENT = "User-Agent: capture.py (http-servlets)\r\n"


def exchange(port, request: bytes) -> bytes:
    """Sends the raw request and reads until the server closes the connection."""
    with socket.create_connection(("localhost", port), timeout=10) as sock:
        sock.sendall(request)
        chunks = []
        while True:
            chunk = sock.recv(65536)
            if not chunk:
                return b"".join(chunks)
            chunks.append(chunk)


def get(path, extra=""):
    return (f"GET {path} HTTP/1.1\r\nHost: localhost:{TOMCAT_PORT}\r\n{AGENT}"
            f"Accept: */*\r\n{extra}Connection: close\r\n\r\n").encode()


def post(port, path, content_type, body: bytes):
    head = (f"POST {path} HTTP/1.1\r\nHost: localhost:{port}\r\n{AGENT}"
            f"Content-Type: {content_type}\r\nContent-Length: {len(body)}\r\nConnection: close\r\n\r\n")
    return head.encode() + body


def tomcat_log():
    """Lines the servlets printed with System.out (the rest of the Tomcat log is its own INFO output)."""
    try:
        out = subprocess.run(["docker", "compose", "logs", "--no-log-prefix", "tomcat"], cwd=ROOT,
                             capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60).stdout
    except (OSError, subprocess.SubprocessError):
        return []
    return [line for line in out.splitlines() if line.startswith(("FirstServlet", "{", "x-", "host:", "user-agent:",
                                                                  "accept:", "connection:", "name="))]


def commit():
    sha = os.environ.get("GITHUB_SHA")
    if sha:
        return sha[:7]
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True,
                              text=True, timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


JSON_BODY = (ROOT / "Http_Servlets" / "src" / "resources" / "json_example.json").read_bytes()
FORM_BODY = ("name=" + urllib.parse.quote_plus("Привет, сервлет!")).encode()

SCENARIOS = [
    # id, target, title, who handles it, request bytes
    ("own-get", "own", "GET / to the hand-made server", "server.HttpServer",
     get("/").replace(f"localhost:{TOMCAT_PORT}".encode(), f"localhost:{SERVER_PORT}".encode())),
    ("own-post", "own", "POST json_example.json to the hand-made server (like HttpClientRunner)", "server.HttpServer",
     post(SERVER_PORT, "/", "application/json", JSON_BODY)),
    ("index", "tomcat", "GET / - the start page", "DefaultServlet (static file index.html)", get("/")),
    ("first", "tomcat", "GET /first", "FirstServlet.doGet (web.xml mapping)", get("/first")),
    ("params", "tomcat", "GET /first with query parameters", "FirstServlet.doGet",
     get("/first?param=hello&city=Minsk&city=Warsaw")),
    ("headers", "tomcat", "GET /first?headers", "FirstServlet.doGet", get("/first?headers", "X-Course: dmdev\r\n")),
    ("post-json", "tomcat", "POST /first with a JSON body", "FirstServlet.doPost",
     post(TOMCAT_PORT, "/first", "application/json", '{"name":"Привет"}'.encode())),
    ("post-form", "tomcat", "POST /first from an HTML form", "FirstServlet.doPost",
     post(TOMCAT_PORT, "/first", "application/x-www-form-urlencoded", FORM_BODY)),
    ("download", "tomcat", "GET /download", "DownloadServlet.doGet (@WebServlet mapping)", get("/download")),
    ("put", "tomcat", "PUT /first - a method the servlet does not implement", "HttpServlet.service -> 405",
     b"PUT /first HTTP/1.1\r\nHost: localhost:%d\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" % TOMCAT_PORT),
    ("missing", "tomcat", "GET /missing - no servlet for this URL", "Tomcat -> 404", get("/missing")),
]


def main():
    # a fresh Tomcat container: its log then holds only these requests, and FirstServlet.init() happens
    # on the first captured request to /first, as it does for a visitor
    subprocess.run(["docker", "compose", "up", "-d", "--force-recreate", "--wait"], cwd=ROOT, check=True,
                   capture_output=True)
    scenarios = []
    for sid, target, title, handler, request in SCENARIOS:
        port = SERVER_PORT if target == "own" else TOMCAT_PORT
        response = exchange(port, request)
        scenarios.append({
            "id": sid, "target": target, "title": title, "handler": handler,
            "request": request.decode("utf-8"), "response": response.decode("utf-8", errors="replace"),
        })
        status_line = response.split(b"\r\n", 1)[0].decode()
        print(f"{sid:10} {status_line}")
    data = {
        "captured_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "where": "GitHub Actions" if os.environ.get("GITHUB_ACTIONS") else "a local run",
        "commit": commit(),
        "tomcat_log": tomcat_log(),
        "scenarios": scenarios,
    }
    out = HERE / "captures.js"
    out.write_text("// generated by site/capture.py - real requests and responses, do not edit by hand\n"
                   "window.CAPTURES = " + json.dumps(data, ensure_ascii=False, indent=1) + ";\n",
                   encoding="utf-8", newline="\n")
    print(f"saved {len(scenarios)} exchanges to {out}")


if __name__ == "__main__":
    main()
