param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'
$work=Join-Path $PSScriptRoot '.work'
New-Item -ItemType Directory -Force $work | Out-Null
$source=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../android/assets/remove.sh'))
$source=$source.Replace('/data/local/tcl-mirakurun','/data/local/tcl-mirakurun-delete-test').Replace('/data/adb/service.d/99-tcl-mirakurun.sh','/data/local/tmp/mirakurun-delete-test-boot.sh')
$fixture=Join-Path $work 'remove-fixture.sh'
[IO.File]::WriteAllText($fixture,$source,[Text.UTF8Encoding]::new($false))
& adb -s $Serial push $fixture /data/local/tmp/mirakurun-remove-fixture.sh
if($LASTEXITCODE -ne 0){throw 'Fixture push failed'}
& adb -s $Serial push (Join-Path $PSScriptRoot 'remove-fixture.sh') /data/local/tmp/mirakurun-test-remove.sh
if($LASTEXITCODE -ne 0){throw 'Test push failed'}
& adb -s $Serial shell "su -c 'sh /data/local/tmp/mirakurun-test-remove.sh'"
if($LASTEXITCODE -ne 0){throw 'Removal fixture test failed; inspect the isolated test directory'}
