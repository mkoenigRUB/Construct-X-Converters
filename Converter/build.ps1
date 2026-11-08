<#
.SYNOPSIS
    Build- und Start-Skript für die Construct-X Converter (Maven-Workaround für die Sandbox).

.DESCRIPTION
    Maven hängt in der Sandbox-Umgebung (Launcher wird blockiert). Dieses Skript
    übernimmt den Build manuell mit javac/jar:
      1. Core-Modul kompilieren (javac)
      2. Core-Jar bauen und nach web/lib kopieren
      3. Web-Modul kompilieren (javac)
      4. Ressourcen (static, examples, properties) nach web/target/classes synchronisieren
    Optional: Service starten (-Start) oder Tests ausführen (-Test, benötigt JUnit-Console-Launcher).

.PARAMETER Start
    Startet den Web-Service nach dem Build auf Port 8081 (im Hintergrund).

.PARAMETER Port
    Port für den Web-Service (Standard: 8081).

.PARAMETER Clean
    Löscht vorher die target-Verzeichnisse von core und web.

.EXAMPLE
    .\build.ps1
    .\build.ps1 -Start
    .\build.ps1 -Clean -Start -Port 8082
#>
param(
    [switch]$Start,
    [int]$Port = 8081,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------------
# Pfade und Werkzeuge
# ------------------------------------------------------------------
$Root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$Core    = Join-Path $Root 'core'
$Web     = Join-Path $Root 'web'
$Java    = 'C:\Program Files\Java\jdk-20\bin\java.exe'
$Javac   = 'C:\Program Files\Java\jdk-20\bin\javac.exe'
$Jar     = 'C:\Program Files\Java\jdk-20\bin\jar.exe'
$Lib     = Join-Path $Web 'lib'
$CoreCls = Join-Path $Core 'target\classes'
$WebCls  = Join-Path $Web 'target\classes'
$CoreJar = Join-Path $Core 'target\construct-x-converters-core-0.1.0-SNAPSHOT.jar'

foreach ($tool in @($Java, $Javac, $Jar)) {
    if (-not (Test-Path $tool)) { throw "Werkzeug nicht gefunden: $tool" }
}
if (-not (Test-Path $Lib)) { throw "web/lib fehlt. Bitte einmalig die Jars aus dem lokalen Maven-Repository kopieren (siehe README)." }

function Invoke-Step([string]$name, [scriptblock]$block) {
    Write-Host "`n==> $name" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $block
    $sw.Stop()
    Write-Host "    fertig in $($sw.ElapsedMilliseconds) ms" -ForegroundColor DarkGray
}

# ------------------------------------------------------------------
# 0. Optional: Clean
# ------------------------------------------------------------------
if ($Clean) {
    Invoke-Step 'Clean' {
        Remove-Item (Join-Path $Core 'target') -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item (Join-Path $Web 'target')  -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host '    target-Verzeichnisse entfernt'
    }
}

# ------------------------------------------------------------------
# 1. Core kompilieren
# ------------------------------------------------------------------
Invoke-Step 'Core kompilieren (javac)' {
    New-Item -ItemType Directory -Path $CoreCls -Force | Out-Null
    $coreSrcs = Get-ChildItem (Join-Path $Core 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & $Javac -encoding UTF-8 -cp "$Lib\*" -d $CoreCls $coreSrcs
    if ($LASTEXITCODE -ne 0) { throw "Core-Kompilierung fehlgeschlagen (Exit $LASTEXITCODE)" }
    # Ressourcen (u. a. gaeb-xsd/) in die Class-Verzeichnis kopieren, damit sie ins Jar kommen
    $coreRes = Join-Path $Core 'src\main\resources'
    if (Test-Path $coreRes) {
        Copy-Item (Join-Path $coreRes '*') $CoreCls -Recurse -Force
    }
    Write-Host "    $($coreSrcs.Count) Quellen kompiliert, Ressourcen kopiert"
}

# ------------------------------------------------------------------
# 2. Core-Jar bauen und nach web/lib kopieren
# ------------------------------------------------------------------
Invoke-Step 'Core-Jar bauen' {
    & $Jar --create --file $CoreJar -C $CoreCls .
    if ($LASTEXITCODE -ne 0) { throw "Core-Jar fehlgeschlagen (Exit $LASTEXITCODE)" }
    Copy-Item $CoreJar $Lib -Force
    Write-Host "    $CoreJar -> web/lib"
}

# ------------------------------------------------------------------
# 3. Web kompilieren
# ------------------------------------------------------------------
Invoke-Step 'Web kompilieren (javac)' {
    New-Item -ItemType Directory -Path $WebCls -Force | Out-Null
    $webSrcs = Get-ChildItem (Join-Path $Web 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & $Javac -encoding UTF-8 -cp "$Lib\*;$CoreCls" -d $WebCls $webSrcs
    if ($LASTEXITCODE -ne 0) { throw "Web-Kompilierung fehlgeschlagen (Exit $LASTEXITCODE)" }
    Write-Host "    $($webSrcs.Count) Quellen kompiliert"
}

# ------------------------------------------------------------------
# 4. Ressourcen synchronisieren
# ------------------------------------------------------------------
Invoke-Step 'Ressourcen synchronisieren' {
    New-Item -ItemType Directory -Path (Join-Path $WebCls 'static') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $WebCls 'examples') -Force | Out-Null
    Copy-Item (Join-Path $Web 'src\main\resources\static\*') (Join-Path $WebCls 'static') -Recurse -Force
    Copy-Item (Join-Path $Web 'src\main\resources\examples\*') (Join-Path $WebCls 'examples') -Recurse -Force
    Copy-Item (Join-Path $Web 'src\main\resources\application.properties') (Join-Path $WebCls 'application.properties') -Force
    Write-Host '    static, examples, application.properties kopiert'
}

Write-Host "`nBuild erfolgreich." -ForegroundColor Green

# ------------------------------------------------------------------
# 5. Optional: Service starten
# ------------------------------------------------------------------
if ($Start) {
    Write-Host "`n==> Web-Service starten (Port $Port)" -ForegroundColor Cyan
    $cp = "$Lib\*;$WebCls"
    $proc = Start-Process -FilePath $Java -ArgumentList "-cp","`"$cp`"",'org.constructx.converter.web.ConverterWebApplication',"--server.port=$Port" -NoNewWindow -PassThru
    Write-Host "    Java-PID: $($proc.Id)"
    # Health-Check
    $up = $false
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 5
        try {
            $r = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/health" -TimeoutSec 5
            Write-Host "    Service läuft: $($r | ConvertTo-Json -Compress)" -ForegroundColor Green
            $up = $true
            break
        } catch { }
    }
    if (-not $up) {
        Write-Host "    WARNUNG: Service war nach 60 s nicht erreichbar (PID $($proc.Id) läuft ggf. noch hoch)." -ForegroundColor Yellow
    }
    Write-Host "    Frontend: http://localhost:$Port/"
    Write-Host "    Stoppen:  Stop-Process -Id $($proc.Id)"
}
