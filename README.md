# Sancris Cititor (Android)

App nativă Kotlin + Compose pentru citire contoare via QR + foto.
Sideload-uită direct pe telefoane (fără Play Store).

## Setup keystore (o singură dată per dezvoltator)

Release-urile trebuie semnate cu **același keystore** pentru ca update-urile să se
aplice peste APK-ul instalat anterior. Daca pierzi keystore-ul, trebuie să
dezinstalezi app-ul de pe toate telefoanele și să generezi unul nou.

```powershell
# In radacina proiectului:
keytool -genkeypair -v `
    -keystore keystore\sancris-release.jks `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -alias sancris
```
Răspunzi la întrebări (CN, O, etc.) cu ce vrei. Notează parolele.

Adaugă în `local.properties` (NU se urca pe git):

```properties
RELEASE_STORE_FILE=keystore/sancris-release.jks
RELEASE_STORE_PASSWORD=parola_keystore
RELEASE_KEY_ALIAS=sancris
RELEASE_KEY_PASSWORD=parola_cheie
```

> **Backup keystore:** salvează `keystore\sancris-release.jks` și parolele
> într-un loc sigur (1Password, USB criptat). Fără el nu mai poți publica
> update-uri compatibile.

## Setup gh CLI (o singură dată)

```powershell
winget install GitHub.cli
gh auth login
```
Alege GitHub.com → HTTPS → autentificare prin browser.

## Release flow

```powershell
# Bumpuieste doar versionCode (versionName ramane):
.\scripts\release.ps1

# Sau cu versionName nou:
.\scripts\release.ps1 -VersionName "0.2.0"
```

Scriptul:
1. Bumpuieste `versionCode` (+1) si optional `versionName` in `app/build.gradle.kts`
2. Build APK release semnat (`./gradlew :app:assembleRelease`)
3. Sterge release-ul GitHub vechi tagged `latest` si urca unul nou cu APK-ul
4. Commit + push version bump-ul pe `main`

App-urile instalate vor detecta versiunea nouă la următoarea pornire.

## Auto-update (cum funcționează în app)

La pornire, app-ul interoghează `https://api.github.com/repos/alexcalcan/sancris_android/releases/tags/latest`.
Parsează `versionCode=N` din body-ul release-ului și, dacă e mai mare decât
`BuildConfig.VERSION_CODE`, afișează un dialog:
- **Instalează** → download APK în cache → trigger Package Installer (cere o
  singură dată permisiunea "Install unknown apps" pentru sursa app-ului)
- **Mai târziu** → dispare până la următoarea pornire

## Dezvoltare locală

```powershell
# Build debug + instalare pe device-ul USB-conectat:
.\gradlew.bat :app:installDebug
```

Debug-urile au `applicationId` cu sufix `.debug` deci nu intră în conflict cu
release-ul instalat.
