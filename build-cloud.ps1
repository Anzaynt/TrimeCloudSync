param([Parameter(Mandatory=$true)][string]$Sdk,[Parameter(Mandatory=$true)][string]$Jdk,[string]$Gradle)
$ErrorActionPreference='Stop'
if (-not $Gradle) { $Gradle=Join-Path $PSScriptRoot 'gradlew.bat' }
$env:JAVA_HOME=(Resolve-Path -LiteralPath $Jdk).Path
$sdkPath=(Resolve-Path -LiteralPath $Sdk).Path.Replace('\','/')
$escaped=New-Object System.Text.StringBuilder
foreach ($character in $sdkPath.ToCharArray()) {
    if ([int]$character -gt 127) { [void]$escaped.Append(('\u{0:x4}' -f [int]$character)) }
    else { [void]$escaped.Append($character) }
}
Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local.properties') -Value ('sdk.dir='+$escaped.ToString()) -Encoding ascii
$env:CI_NAME='CloudSyncFork'
$env:BUILD_COMMIT_HASH='af23c4a631274d118ed76ae77e6dc8d8f7024c56'
$env:BUILD_GIT_REPO='https://github.com/Anzaynt/TrimeCloudSync'
$env:BUILD_VERSION_NAME='3.3.12-cloud0.2.8'
& $Gradle -p $PSScriptRoot app:assembleRelease --no-daemon --console=plain --max-workers=4
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
