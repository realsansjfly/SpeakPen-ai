param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline,
    [string]$OutputDirectory = '',
    [string]$ApkName = 'SpeakPen-0.6.0-dev02-debug.apk',
    [switch]$Unpaired
)
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    $studioRuntime = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $studioRuntime 'bin\javac.exe')) { $JavaHome = $studioRuntime }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw '需要完整的 JDK 17。请设置 JAVA_HOME，或传入 -JavaHome 目录。'
}
function Copy-TreeFiles([string]$sourceRoot, [string]$destinationRoot) {
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File | ForEach-Object {
        $relativePath = $_.FullName.Substring($sourceRoot.TrimEnd('\').Length + 1)
        $destinationPath = Join-Path $destinationRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPath) -Force | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $destinationPath -Force
    }
}
$oldJavaHome = $env:JAVA_HOME
$oldToolOptions = $env:JAVA_TOOL_OPTIONS
$oldServiceUrl = $env:SPEAKPEN_BACKEND_URL
$oldServiceToken = $env:SPEAKPEN_SERVICE_TOKEN
try {
    $connectionPath = Join-Path $workspacePath '.local\connection.json'
    if ($Unpaired) {
        $env:SPEAKPEN_BACKEND_URL = ''
        $env:SPEAKPEN_SERVICE_TOKEN = ''
    } elseif (Test-Path -LiteralPath $connectionPath) {
        $connection = Get-Content -LiteralPath $connectionPath -Raw | ConvertFrom-Json
        $env:SPEAKPEN_BACKEND_URL = $connection.baseUrl
        $env:SPEAKPEN_SERVICE_TOKEN = $connection.accessToken
    }
    $env:JAVA_HOME = $JavaHome
    $env:JAVA_TOOL_OPTIONS = ($oldToolOptions + ' -Dfile.encoding=UTF-8').Trim()
    $javaExe = Join-Path $JavaHome 'bin\java.exe'
    $probePath = Join-Path $PSScriptRoot 'LoopbackProbe.java'
    $probeOutput = & $javaExe $probePath 2>&1
    if ($LASTEXITCODE -ne 0) {
        $unavailablePath = Join-Path $workspacePath ('.unavailable-unix-sockets-' + [guid]::NewGuid().ToString('N'))
        $unavailablePath = $unavailablePath.Replace('\', '/')
        $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Djdk.net.unixdomain.tmpdir="' + $unavailablePath + '"').Trim()
        Write-Output 'Java Selector 检查失败，本次构建使用 TCP 回退。'
    }
    $buildPath = $workspacePath
    if ($workspacePath -match '[^\x00-\x7F]') {
        # JDK 17/Gradle worker argument files can misread Chinese classpaths on Windows.
        $buildPath = Join-Path $env:LOCALAPPDATA ('Temp\SpeakPen-build-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
        New-Item -ItemType Directory -Path $buildPath -Force | Out-Null
        foreach ($relativeInput in @('app\src', 'gradle')) {
            Copy-TreeFiles (Join-Path $workspacePath $relativeInput) (Join-Path $buildPath $relativeInput)
        }
        foreach ($relativeInput in @('app\build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'local.properties')) {
            $sourcePath = Join-Path $workspacePath $relativeInput
            if (Test-Path -LiteralPath $sourcePath) {
                $destinationPath = Join-Path $buildPath $relativeInput
                New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPath) -Force | Out-Null
                Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
            }
        }
        Write-Output "使用 ASCII 临时目录完成构建和测试：$buildPath"
    }
    $arguments = @('-p', $buildPath, ':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '--no-daemon', '--console=plain')
    if ($Offline) { $arguments += '--offline' }
    & (Join-Path $buildPath 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw '构建或验证失败，请查看上方诊断。' }
    $deliveryDirectory = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $workspacePath 'deliverables' }
    New-Item -ItemType Directory -Path $deliveryDirectory -Force | Out-Null
    $deliveryPath = Join-Path $deliveryDirectory $ApkName
    Copy-Item -LiteralPath (Join-Path $buildPath 'app\build\outputs\apk\debug\app-debug.apk') -Destination $deliveryPath -Force
    Copy-TreeFiles (Join-Path $buildPath 'app\build\reports') (Join-Path $deliveryDirectory 'reports')
    Copy-TreeFiles (Join-Path $buildPath 'app\build\test-results\testDebugUnitTest') (Join-Path $deliveryDirectory 'test-results')
    $fixturePath = Join-Path $buildPath 'app\build\outputs\conversation-config-live-requests.json'
    if (Test-Path -LiteralPath $fixturePath) {
        Copy-Item -LiteralPath $fixturePath -Destination (Join-Path $deliveryDirectory 'conversation-config-live-requests.json') -Force
    }
    $topicFixturePath = Join-Path $buildPath 'app\build\outputs\debug-topic-live-requests.json'
    if (Test-Path -LiteralPath $topicFixturePath) {
        Copy-Item -LiteralPath $topicFixturePath -Destination (Join-Path $deliveryDirectory 'debug-topic-live-requests.json') -Force
    }
    Write-Output "APK: $deliveryPath"
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:JAVA_TOOL_OPTIONS = $oldToolOptions
    $env:SPEAKPEN_BACKEND_URL = $oldServiceUrl
    $env:SPEAKPEN_SERVICE_TOKEN = $oldServiceToken
}





