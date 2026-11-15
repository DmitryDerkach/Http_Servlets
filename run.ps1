# 2026: one-command run of the HTTP / Servlets practice (Windows PowerShell). Needs JDK 11+.
#   .\run.ps1                  the same as .\run.ps1 web
#   .\run.ps1 web              build the WAR, (re)start Tomcat 9 in Docker, call every servlet with curl
#                              and leave Tomcat running: open http://localhost:8080/ in a browser
#   .\run.ps1 server [socket]  start the hand-made HttpServer on port 9000 (package server, or its copy in socket)
#                              and send it requests from curl and the three Java clients
#   .\run.ps1 client           the internet examples: UrlExample get/file and HttpClientExample get (google.com)
#   .\run.ps1 test             JUnit tests: servlets on an embedded Tomcat + the hand-made server (no Docker)
#   .\run.ps1 stop             stop and remove the Tomcat container
# Only "web" needs Docker. $env:TOMCAT_PORT = "8081" before .\run.ps1 web uses another port if 8080 is taken.
param(
    [string]$Command = "web",
    [string]$Package = "server"
)
# No $ErrorActionPreference = "Stop": Windows PowerShell 5.1 would treat docker's progress output on stderr
# as an error. Each step's exit code is checked instead.
$script:code = 0

function Invoke-Step([scriptblock]$Block) {
    if ($script:code -ne 0) { return }
    & $Block
    $script:code = $LASTEXITCODE
}

function Write-Title([string]$Text) {
    Write-Host ""
    Write-Host "== $Text ==" -ForegroundColor Cyan
}

$port = if ($env:TOMCAT_PORT) { $env:TOMCAT_PORT } else { "8080" }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
# JDK 17 on Windows would print Cyrillic as "?????" without these
$javaOpts = @("-Dstdout.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")

# the examples read src/resources/... relative to the working folder, so they run from Http_Servlets\
function Invoke-Java([string[]]$JavaArgs) {
    Invoke-Step { & $java @javaOpts -cp target/classes @JavaArgs }
}

function Invoke-Curl([string[]]$CurlArgs) {
    Write-Title ("curl " + ($CurlArgs -join " "))
    Invoke-Step { curl.exe -sS @CurlArgs }
    Write-Host ""
}

# Java and curl print UTF-8; let the console decode it as UTF-8 too (restored at the end)
$originalEncoding = [Console]::OutputEncoding
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false } catch { }

# run from the repository folder, and return the caller to their folder at the end
Push-Location $PSScriptRoot
try {
    switch ($Command) {
        "web" {
            Set-Location Http_Servlets
            Invoke-Step { .\mvnw.cmd -B -q package -DskipTests }
            Set-Location ..
            Invoke-Step { docker compose up -d --force-recreate --wait }
            if ($script:code -ne 0) { break }
            $url = "http://localhost:$port"
            Invoke-Curl @("-i", "$url/first")
            Invoke-Curl @("$url/first?param=hello&city=Minsk&city=Warsaw")
            Invoke-Curl @("-i", "$url/download")
            # {"name":"<Privet in Cyrillic>"}: this script is ASCII-only (Windows PowerShell 5.1 reads scripts
            # without a BOM as ANSI), and curl.exe would turn Cyrillic arguments into "??????" - so the body
            # is built from character codes and sent from a UTF-8 file
            $privet = -join [char[]](0x41F, 0x440, 0x438, 0x432, 0x435, 0x442)
            $body = '{"name":"' + $privet + '"}'
            $bodyFile = Join-Path ([IO.Path]::GetTempPath()) "http-servlets-body.json"
            [IO.File]::WriteAllText($bodyFile, $body, (New-Object System.Text.UTF8Encoding $false))
            Write-Title "curl -X POST -H `"Content-Type: application/json`" --data-binary '$body' $url/first"
            Invoke-Step { curl.exe -sS -X POST -H "Content-Type: application/json" --data-binary "@$bodyFile" "$url/first" }
            Remove-Item $bodyFile
            Write-Title "Tomcat log: what the servlets printed with System.out"
            docker compose logs --no-log-prefix tomcat | Select-String -Pattern '^(FirstServlet|\{)' | ForEach-Object { $_.Line }
            Write-Title "Tomcat is running: open $url/ in a browser (.\run.ps1 stop stops it)"
        }
        "server" {
            if (@("server", "socket") -notcontains $Package) {
                Write-Host "Unknown package: $Package (expected: server or socket)"
                $script:code = 1
                break
            }
            Set-Location Http_Servlets
            Invoke-Step { .\mvnw.cmd -B -q compile }
            if ($script:code -ne 0) { break }
            curl.exe -s -o NUL http://localhost:9000/
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Port 9000 is already in use - stop the program that listens on it first."
                $script:code = 1
                break
            }
            $log = "target\http-server.log"
            $serverArgs = $javaOpts + @("-cp", "target/classes", "$Package.HttpServerRunner")
            $server = Start-Process -FilePath $java -ArgumentList $serverArgs -WorkingDirectory (Get-Location).Path `
                -RedirectStandardOutput $log -RedirectStandardError "target\http-server-errors.log" -NoNewWindow -PassThru
            try {
                # the first request also waits until the server is up (no separate probe request in the server log);
                # -s without -S: "connection refused" while the server is starting is expected, not an error
                Write-Title "curl -i http://localhost:9000/"
                Invoke-Step { curl.exe -s -i --retry 50 --retry-delay 1 --retry-connrefused http://localhost:9000/ }
                Write-Host ""
                Write-Title "$Package.HttpClientRunner: java.net.http.HttpClient, POST json_example.json"
                Invoke-Java @("$Package.HttpClientRunner")
                Write-Title "client.UrlExample post: URLConnection, POST json_example.json"
                Invoke-Java @("client.UrlExample", "post")
                Write-Title "client.HttpClientExample post: HttpClient, POST from a file"
                Invoke-Java @("client.HttpClientExample", "post")
            } finally {
                Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
                $server.WaitForExit()
            }
            Write-Title "What $Package.HttpServer received (its console)"
            Get-Content $log -Encoding UTF8
        }
        "client" {
            Set-Location Http_Servlets
            Invoke-Step { .\mvnw.cmd -B -q compile }
            Write-Title "client.UrlExample get: GET https://www.google.com with URLConnection"
            Invoke-Java @("client.UrlExample", "get")
            Write-Title "client.UrlExample file: the same URLConnection reads a local file"
            Invoke-Java @("client.UrlExample", "file")
            Write-Title "client.HttpClientExample get: GET https://www.google.com with HttpClient"
            Invoke-Java @("client.HttpClientExample", "get")
        }
        "test" {
            Set-Location Http_Servlets
            Invoke-Step { .\mvnw.cmd -B test }
        }
        "stop" {
            Invoke-Step { docker compose down }
        }
        default {
            Write-Host "Unknown command: $Command (expected: web, server [socket], client, test, stop)"
            $script:code = 1
        }
    }
} finally {
    Pop-Location
    try { [Console]::OutputEncoding = $originalEncoding } catch { }
}
exit $script:code
