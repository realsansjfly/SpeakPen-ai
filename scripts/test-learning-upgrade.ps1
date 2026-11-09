$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$testPath = Join-Path $env:LOCALAPPDATA 'Temp/SpeakPen-learning-check'
New-Item -ItemType Directory -Path $testPath -Force | Out-Null
foreach ($inputPath in @('app/src', 'gradle')) {
    $sourcePath = Join-Path $workspacePath $inputPath
    $destinationPath = Join-Path $testPath $inputPath
    New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
    & robocopy $sourcePath $destinationPath /E /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) { throw 'Source copy failed' }
}
foreach ($inputPath in @('app/build.gradle.kts','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','local.properties')) {
    Copy-Item -LiteralPath (Join-Path $workspacePath $inputPath) -Destination (Join-Path $testPath $inputPath) -Force
}
$env:JAVA_HOME = 'C:/EmojiJdk17'
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Djdk.net.unixdomain.tmpdir=C:/speakpen-unavailable-unix-sockets'
# Deliberately exclude assemble/package/APK tasks.
& (Join-Path $testPath 'gradlew.bat') -p $testPath :app:testDebugUnitTest :app:lintDebug --offline --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw 'JVM/lint verification failed' }
Write-Output "Verification reports: $testPath/app/build/reports"
