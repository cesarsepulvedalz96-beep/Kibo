param([ValidateSet('run', 'test', 'verify', 'package')][string]$Task = 'run')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
$isApplicationRun = $Task -eq 'run'
$env:DEBUG = 'false'
if (-not $isApplicationRun) {
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:BOOTSTRAP_ADMIN_EMAIL = ''
    $env:BOOTSTRAP_ADMIN_PASSWORD = ''
    $env:DEMO_USER_PASSWORD = ''
}
$localJava = Join-Path $projectRoot '.tools/java'
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $localJava)) {
    $env:JAVA_HOME = (Get-ChildItem -Directory -LiteralPath $localJava | Select-Object -First 1).FullName
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
$maven = 'mvn.cmd'
if (-not (Get-Command $maven -ErrorAction SilentlyContinue)) {
    $maven = Join-Path $projectRoot '.tools/maven/apache-maven-3.9.11/bin/mvn.cmd'
}
$mavenTask = if ($Task -eq 'run') { 'spring-boot:run' } else { $Task }
& $maven -f (Join-Path $projectRoot 'backend/pom.xml') "-Dmaven.repo.local=$(Join-Path $projectRoot '.tools/m2')" -B -ntp $mavenTask
exit $LASTEXITCODE
