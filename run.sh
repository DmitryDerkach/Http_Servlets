#!/usr/bin/env bash
# 2026: one-command run of the HTTP / Servlets practice (Linux, macOS, Git Bash on Windows). Needs JDK 11+.
#   ./run.sh                  the same as ./run.sh web
#   ./run.sh web              build the WAR, (re)start Tomcat 9 in Docker, call every servlet with curl
#                             and leave Tomcat running: open http://localhost:8080/ in a browser
#   ./run.sh server [socket]  start the hand-made HttpServer on port 9000 (package server, or its copy in socket)
#                             and send it requests from curl and the three Java clients
#   ./run.sh client           the internet examples: UrlExample get/file and HttpClientExample get (google.com)
#   ./run.sh test             JUnit tests: servlets on an embedded Tomcat + the hand-made server (no Docker)
#   ./run.sh stop             stop and remove the Tomcat container
# Only "web" needs Docker. TOMCAT_PORT=8081 ./run.sh web uses another port if 8080 is taken.
set -euo pipefail
cd "$(dirname "$0")"

PORT="${TOMCAT_PORT:-8080}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
# JDK 17 on Windows would print Cyrillic as "?????" without these
JAVA_OPTS=(-Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8)

# Git Bash would rewrite container paths into Windows paths; docker commands only
docker_cmd() { MSYS_NO_PATHCONV=1 docker "$@"; }

# the examples read src/resources/... relative to the working folder, so they run from Http_Servlets/
run_java() { (cd Http_Servlets && "$JAVA" "${JAVA_OPTS[@]}" -cp target/classes "$@"); }

build() { (cd Http_Servlets && ./mvnw -B -q "$@"); }

title() { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }

show() {
  title "curl $*"
  curl -sS "$@"
  echo
}

web() {
  build package -DskipTests
  docker_cmd compose up -d --force-recreate --wait
  local url="http://localhost:$PORT"
  show -i "$url/first"
  show "$url/first?param=hello&city=Minsk&city=Warsaw"
  show -i "$url/download"
  # the body goes through stdin: curl on Windows would turn Cyrillic command-line arguments into "??????"
  title "curl -X POST -H \"Content-Type: application/json\" --data-binary '{\"name\":\"Привет\"}' $url/first"
  printf '%s' '{"name":"Привет"}' | curl -sS -X POST -H "Content-Type: application/json" --data-binary @- "$url/first"
  echo
  title "Tomcat log: what the servlets printed with System.out"
  docker_cmd compose logs --no-log-prefix tomcat | grep -E '^(FirstServlet|\{)' || true
  title "Tomcat is running: open $url/ in a browser (./run.sh stop stops it)"
}

server() {
  local pkg="${1:-server}"
  case "$pkg" in server|socket) ;; *) echo "Unknown package: $pkg (expected: server or socket)" >&2; exit 1 ;; esac
  build compile
  if curl -s -o /dev/null http://localhost:9000/; then
    echo "Port 9000 is already in use - stop the program that listens on it first." >&2
    exit 1
  fi
  local log=Http_Servlets/target/http-server.log
  # exec: $! must be the java process itself (not a subshell), so that kill stops the server at the end
  (cd Http_Servlets && exec "$JAVA" "${JAVA_OPTS[@]}" -cp target/classes "$pkg.HttpServerRunner") > "$log" 2>&1 &
  local pid=$!
  trap 'kill '"$pid"' 2>/dev/null || true' EXIT
  # the first request also waits until the server is up (no separate probe request in the server log);
  # -s without -S: "connection refused" while the server is starting is expected, not an error
  title "curl -i http://localhost:9000/"
  curl -s -i --retry 50 --retry-delay 1 --retry-connrefused http://localhost:9000/
  echo
  title "$pkg.HttpClientRunner: java.net.http.HttpClient, POST json_example.json"
  run_java "$pkg.HttpClientRunner"
  title "client.UrlExample post: URLConnection, POST json_example.json"
  run_java client.UrlExample post
  title "client.HttpClientExample post: HttpClient, POST from a file"
  run_java client.HttpClientExample post
  title "What $pkg.HttpServer received (its console)"
  cat "$log"
}

client() {
  build compile
  title "client.UrlExample get: GET https://www.google.com with URLConnection"
  run_java client.UrlExample get
  title "client.UrlExample file: the same URLConnection reads a local file"
  run_java client.UrlExample file
  title "client.HttpClientExample get: GET https://www.google.com with HttpClient"
  run_java client.HttpClientExample get
}

case "${1:-web}" in
  web)    web ;;
  server) server "${2:-server}" ;;
  client) client ;;
  test)   (cd Http_Servlets && ./mvnw -B test) ;;
  stop)   docker_cmd compose down ;;
  *)
    echo "Unknown command: $1 (expected: web, server [socket], client, test, stop)" >&2
    exit 1
    ;;
esac
