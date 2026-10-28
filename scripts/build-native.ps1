param([string]$NdkRoot=$env:ANDROID_NDK_HOME)
$ErrorActionPreference='Stop'
& "$PSScriptRoot/../vendor/tcl/native/build.ps1" -NdkRoot $NdkRoot
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
Copy-Item "$PSScriptRoot/../vendor/tcl/native/build/libtsd.so","$PSScriptRoot/../vendor/tcl/native/build/tsctl" "$PSScriptRoot/../android/assets/"
