# Debug-Crash: Logcat für Creator-App starten
# Nach dem Crash: Strg+C drücken, dann creator-crash-log.txt prüfen

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$logFile = Join-Path $PSScriptRoot "creator-crash-log.txt"

# Logcat leeren
& $adb -s emulator-5554 logcat -c
Write-Host "Logcat geleert. Starte Aufzeichnung auf Emulator (emulator-5554)..." -ForegroundColor Green
Write-Host "Reproduziere den Crash (wechsel auf Creations-Tab), dann Strg+C druecken." -ForegroundColor Yellow
Write-Host "Logs werden in: $logFile" -ForegroundColor Cyan

# Logcat: AndroidRuntime (Crashes) + com.eazpire.creator
# Format: tag1:priority tag2:priority ... *:S (S=silent fuer Rest)
& $adb -s emulator-5554 logcat -v time AndroidRuntime:E "com.eazpire.creator:V" *:S 2>&1 | Tee-Object -FilePath $logFile
