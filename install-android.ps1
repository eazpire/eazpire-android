# Install eazpire App auf verbundenes Android-Gerät
# Voraussetzung: USB oder WLAN-Debugging (Wireless debugging) aktiv
#
# WICHTIG: buildDir zeigt auf %TEMP%/eazpire-android-build/ – die APK liegt dort,
# nicht in app/build/... (OneDrive-Vermeidung). gradlew installDebug nutzt den richtigen Pfad.
#
# Wireless: IP:Port aus Entwickleroptionen > Wireless debugging > „IP-Adresse & Port“.
# Leer lassen, wenn nur USB genutzt wird.
$WirelessAdb = "192.168.3.12:45129"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host "Fehler: adb nicht gefunden unter $adb" -ForegroundColor Red
    exit 1
}

if ($WirelessAdb -ne "") {
    Write-Host "Verbinde adb (WLAN): $WirelessAdb ..." -ForegroundColor Cyan
    & $adb connect $WirelessAdb 2>&1 | Write-Host
}

Write-Host "Pruefe verbundene Geraete..." -ForegroundColor Cyan
$devices = & $adb devices 2>&1
if ($devices -notmatch "device$") {
    Write-Host ""
    Write-Host "Kein Geraet gefunden!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Bitte pruefen:" -ForegroundColor Yellow
    Write-Host "  1. Pixel 7 per USB verbunden?"
    Write-Host "  2. USB-Debugging aktiviert? (Einstellungen > Entwickleroptionen)"
    Write-Host "  3. Popup 'USB-Debugging zulassen?' auf dem Handy bestaetigt?"
    Write-Host "  4. USB-Modus: Dateiuebertragung (nicht nur Aufladen)"
    Write-Host ""
    Write-Host "Dann dieses Skript erneut ausfuehren." -ForegroundColor Cyan
    exit 1
}

Write-Host "Geraet gefunden. Starte Installation..." -ForegroundColor Green
Set-Location $PSScriptRoot
& ./gradlew installDebug
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Installation erfolgreich!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Installation fehlgeschlagen." -ForegroundColor Red
    exit 1
}
