$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=(Get-Command java).Source
if((& $java --version | Out-String) -notmatch 'openjdk 17\.') { throw 'Use Java 17 for this test-only launcher.' }
foreach($port in @(8182,5179,11025,18025)) {
 $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
 try {$probe.Start()} finally {$probe.Stop()}
}
$root=Join-Path $env:TEMP ('halo-registration-sandbox-'+[guid]::NewGuid())
New-Item -ItemType Directory $root | Out-Null
New-Item -ItemType File (Join-Path $root 'HALO-DISPOSABLE-SANDBOX') | Out-Null
New-Item -ItemType Directory (Join-Path $root 'src/main'),(Join-Path $root 'src/test/resources'),(Join-Path $root 'database') | Out-Null
# Copy Java sources, synthetic Excel fixtures and exactly one schema script; never application config or .env files.
Copy-Item (Join-Path $repo 'HALO/pom.xml') $root
Copy-Item (Join-Path $repo 'HALO/src/main/java') (Join-Path $root 'src/main') -Recurse
Copy-Item (Join-Path $repo 'HALO/src/test/java') (Join-Path $root 'src/test') -Recurse
Copy-Item (Join-Path $repo 'HALO/src/test/resources/registry') (Join-Path $root 'src/test/resources') -Recurse
Copy-Item (Join-Path $repo 'HALO/database/V20261115_01__student_registry.sql') (Join-Path $root 'database')
$mvn=(Get-ChildItem "$env:USERPROFILE/.m2/wrapper/dists" -Filter mvn.cmd -Recurse | Select-Object -First 1).FullName
if(!$mvn){throw 'Cached Maven wrapper distribution is required.'}
& $mvn -o -f (Join-Path $root 'pom.xml') '-Dtest=StudentRegistryExcelCompatibilityTest' test *> (Join-Path $root 'compile.log')
if($LASTEXITCODE -ne 0){throw "Isolated compile failed; inspect $root/compile.log"}
[xml]$report=Get-Content (Join-Path $root 'target/surefire-reports/TEST-com.ptc.halo.StudentRegistryExcelCompatibilityTest.xml')
$classpath=($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
if(!$classpath){throw 'Test runtime classpath unavailable'}
$processes=@()
try {
 $node=(Get-Command node).Source
 $mail=Start-Process $node -ArgumentList @(('"'+(Join-Path $repo 'scripts/local-registration-mail.mjs')+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'mail.log') -RedirectStandardError (Join-Path $root 'mail-error.log')
 $processes+=$mail
 $backend=Start-Process $java -ArgumentList @('-Xmx384m','--add-opens=java.base/java.lang=ALL-UNNAMED','-Djava.security.manager=allow','-cp',('"'+$classpath+'"'),'com.ptc.halo.LocalRegistrationSandbox',('"'+$root+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'backend.log') -RedirectStandardError (Join-Path $root 'backend-error.log')
 $processes+=$backend
 $frontend=Start-Process $node -ArgumentList @(('"'+(Join-Path $repo 'scripts/local-registration-frontend.mjs')+'"'),('"'+$root+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'frontend.log') -RedirectStandardError (Join-Path $root 'frontend-error.log')
 $processes+=$frontend
 $processes | ForEach-Object {[pscustomobject]@{Id=$_.Id;Started=$_.StartTime.Ticks}} | ConvertTo-Json | Set-Content (Join-Path $root 'processes.json')
 Set-Content (Join-Path $env:TEMP 'halo-registration-sandbox-path.txt') $root
 Write-Output "Sandbox directory: $root"
 Write-Output 'Wait for the ready file. Frontend: http://127.0.0.1:5179 ; mail: http://127.0.0.1:18025'
 Write-Output 'Disposable Admin access is in admin-access.txt, never in logs. Use only the sandbox frontend.'
} catch {foreach($process in $processes){if(!$process.HasExited){$process.Kill()}};throw}
