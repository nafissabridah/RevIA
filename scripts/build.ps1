# =====================================================================
#  RevIA - construit l'application complète en un seul JAR exécutable :
#  1) build Angular (production)  2) copie dans backend/src/main/resources/static
#  3) mvn package (tests inclus, sauf -SkipTests)
#  Usage : powershell -ExecutionPolicy Bypass -File scripts\build.ps1 [-SkipTests]
# =====================================================================
param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$racine = Split-Path $PSScriptRoot -Parent
$front = Join-Path $racine 'frontend'
$back = Join-Path $racine 'backend'
$static = Join-Path $back 'src\main\resources\static'

Write-Host "==> 1/3 Build Angular" -ForegroundColor Cyan
Push-Location $front
if (-not (Test-Path 'node_modules')) { npm ci; if ($LASTEXITCODE) { throw 'npm ci a échoué' } }
npx ng build --configuration production
if ($LASTEXITCODE) { throw 'Build Angular en échec' }
Pop-Location

Write-Host "==> 2/3 Copie du front-end dans le back-end" -ForegroundColor Cyan
if (Test-Path $static) { Remove-Item -Recurse -Force $static }
New-Item -ItemType Directory -Force $static | Out-Null
Copy-Item -Recurse -Force (Join-Path $front 'dist\frontend\browser\*') $static

Write-Host "==> 3/3 Build Spring Boot" -ForegroundColor Cyan
Push-Location $back
$args = @('-B', 'package')
if ($SkipTests) { $args += '-DskipTests' }
mvn @args
if ($LASTEXITCODE) { throw 'Build Maven en échec' }
Pop-Location

Write-Host "OK : $back\target\revia.jar" -ForegroundColor Green
