# =====================================================================
#  RevIA - lance l'application (front + back dans le même JAR) sur http://localhost:8085
#  Profil dev : base H2 locale. Le LLM est lu dans le fichier .env à la racine (jamais versionné) :
#      REVIA_AI_PROVIDER=gemini
#      REVIA_AI_API_KEY=<clé Gemini>
#  Sans .env : fournisseur « demo » (analyse par règles, aucune clé nécessaire).
# =====================================================================
$ErrorActionPreference = 'Stop'
$racine = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $racine 'backend\target\revia.jar'
# Variables du fichier .env (clé d'API...) : elles restent sur ce PC
$envFile = Join-Path $racine '.env'
if (Test-Path $envFile) {
    foreach ($ligne in Get-Content $envFile -Encoding UTF8) {
        if ($ligne -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$' -and -not [Environment]::GetEnvironmentVariable($Matches[1])) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim())
        }
    }
}
if (-not (Test-Path $jar)) {
    Write-Host "JAR absent : construction (1re fois, quelques minutes)..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'build.ps1') -SkipTests
}
# Données hors de OneDrive pour éviter les verrous de synchronisation
if (-not $env:REVIA_DATA_DIR) { $env:REVIA_DATA_DIR = Join-Path $env:LOCALAPPDATA 'RevIA\data' }
New-Item -ItemType Directory -Force $env:REVIA_DATA_DIR | Out-Null
$llm = if ($env:REVIA_AI_PROVIDER) { $env:REVIA_AI_PROVIDER } else { 'demo' }
Write-Host "RevIA démarre sur http://localhost:8085 avec le fournisseur IA : $llm  (Ctrl+C pour arrêter)" -ForegroundColor Green
Write-Host "Comptes : sarra@revia.local / Sarra2026!   ·   admin@revia.local / Admin2026!" -ForegroundColor Green
Start-Job { Start-Sleep 18; Start-Process 'http://localhost:8085' } | Out-Null
java -jar $jar
