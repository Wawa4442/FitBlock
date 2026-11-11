param([string]$Serial)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$apk=Join-Path $projectRoot 'dist\FitBlock-MVP.apk'
if(-not (Test-Path -LiteralPath $apk)){throw 'Primero ejecuta scripts\compilar.ps1.'}
if(-not $Serial){
    $devices=@(& $adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if($devices.Count -ne 1){& $adb devices;throw 'Conecta y autoriza un dispositivo, o especifica -Serial.'}
    $Serial=$devices[0]
}
& $adb -s $Serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'No se pudo instalar la APK.'}
& $adb -s $Serial shell am start -n com.fitblock.app/.MainActivity
if($LASTEXITCODE -ne 0){throw 'La app se instaló, pero no se pudo abrir.'}
