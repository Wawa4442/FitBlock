param([switch]$PruebasEnEmulador)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$studioJdk=Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if(Test-Path -LiteralPath (Join-Path $studioJdk 'bin\java.exe')) { $env:JAVA_HOME=$studioJdk }
if(-not $env:JAVA_HOME) { throw 'Instala Android Studio o configura JAVA_HOME con JDK 17 o superior.' }
Push-Location -LiteralPath $projectRoot
try {
    $tasks=@(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug')
    if($PruebasEnEmulador) {
        $sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
        $devices=& (Join-Path $sdk 'platform-tools\adb.exe') devices
        $targets=@($devices | Where-Object { $_ -match '^\S+\s+device$' })
        if($targets.Count -ne 1 -or $targets[0] -notmatch '^emulator-') { throw 'Las pruebas borran datos de FitBlock: conecta exclusivamente un emulador de pruebas con Chrome.' }
        $tasks+= ':app:connectedDebugAndroidTest'
    }
    & .\gradlew.bat @tasks --console=plain
    if($LASTEXITCODE -ne 0){throw 'Falló la compilación o una de las verificaciones.'}
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'dist\FitBlock-MVP.apk' -Force
    $hash=(Get-FileHash -LiteralPath 'dist\FitBlock-MVP.apk' -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  FitBlock-MVP.apk" | Set-Content -Encoding ascii 'dist\FitBlock-MVP.apk.sha256'
    Write-Output "APK lista: $(Join-Path $projectRoot 'dist\FitBlock-MVP.apk')"
} finally { Pop-Location }
