# Build APK release semnat + push pe GitHub ca release "latest".
# Cerinte:
#   - JDK 17+ in PATH (sau JAVA_HOME setat)
#   - gh CLI autentificat (`gh auth login`)
#   - local.properties cu RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD
#
# Folosire:
#   .\scripts\release.ps1                 # bumpuieste versionCode cu 1
#   .\scripts\release.ps1 -VersionName "0.2.0"  # seteaza si versionName

[CmdletBinding()]
param(
    [string]$VersionName = ""
)

$ErrorActionPreference = "Stop"

# Mergi in radacina proiectului (parent folder al scriptului).
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$buildGradle = "app\build.gradle.kts"
$content = Get-Content $buildGradle -Raw

# Citeste versionCode si versionName curente.
$currentCode = [int]([regex]::Match($content, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$currentName = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value

$newCode = $currentCode + 1
$newName = if ($VersionName) { $VersionName } else { $currentName }

Write-Host "Versiune: $currentName (code $currentCode) -> $newName (code $newCode)" -ForegroundColor Cyan

# Bumpuieste in build.gradle.kts.
$content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$content = $content -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newName`""
Set-Content $buildGradle -Value $content -NoNewline

# Build APK release.
Write-Host "Build APK release..." -ForegroundColor Cyan
& .\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Build failed." }

$apkSrc = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkSrc)) { throw "Nu am gasit APK-ul la $apkSrc" }

$apkOut = "sancris-cititor-v$newName.apk"
Copy-Item $apkSrc $apkOut -Force
Write-Host "APK: $apkOut" -ForegroundColor Green

# Verifica gh CLI.
$null = Get-Command gh -ErrorAction Stop

# Sterge release-ul vechi 'latest' (daca exista) si ridica unul nou.
# Folosim mereu acelasi tag 'latest' ca app-ul sa stie unde sa caute.
gh release delete latest --yes --cleanup-tag 2>$null

$body = @"
versionCode=$newCode
versionName=$newName
"@

gh release create latest $apkOut `
    --title "Latest ($newName)" `
    --notes $body `
    --prerelease
if ($LASTEXITCODE -ne 0) { throw "gh release create failed." }

# Commit version bump (daca e curat in afara de build.gradle.kts).
$status = git status --porcelain $buildGradle
if ($status) {
    git add $buildGradle
    git commit -m "Release v$newName (versionCode $newCode)"
    git push origin main
}

Write-Host "Done. Release pe GitHub: latest (v$newName, code $newCode)" -ForegroundColor Green
Write-Host "APK local: $apkOut" -ForegroundColor Green
