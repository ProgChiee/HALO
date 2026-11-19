$ErrorActionPreference='Stop'
$root=(Get-Content (Join-Path $env:TEMP 'halo-registration-sandbox-path.txt')).Trim()
if(!(Test-Path (Join-Path $root 'HALO-DISPOSABLE-SANDBOX'))){throw 'Sandbox marker missing'}
foreach($record in (Get-Content (Join-Path $root 'processes.json') | ConvertFrom-Json)){
 $process=Get-Process -Id $record.Id -ErrorAction SilentlyContinue
 if($process -and $process.StartTime.Ticks -eq $record.Started){Stop-Process -Id $record.Id}
}
foreach($file in @('admin-access.txt','ready')){Remove-Item -LiteralPath (Join-Path $root $file) -ErrorAction SilentlyContinue}
Write-Output 'Sandbox processes stopped. In-memory database and captured messages are discarded. Existing HALO services were not stopped.'
