param(
    [string]$Serial = 'emulator-5554',
    [string]$FixturePath = (Join-Path $PSScriptRoot '..\..\app\src\test\resources\mangalib\ReaderLB_MangaLib_Exact_Fixture.zip'),
    [string]$AdbPath = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

if (-not $AdbPath) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $candidate) { $AdbPath = $candidate }
    else { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
}
$FixturePath = (Resolve-Path -LiteralPath $FixturePath).Path
$root = '/storage/emulated/0/Android/data/ru.libappc/files/manga'
$title = '990001--readerlb-manga-fixture'
$required = @("manga/$title/info.json", "manga/$title/chapters.json", "manga/$title/v1-n1-990101.zip")

function Invoke-Adb([string[]]$Arguments) {
    & $AdbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}

Invoke-Adb @('wait-for-device')
$booted = $false
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $state = & $AdbPath -s $Serial shell getprop sys.boot_completed
    if ($state.Trim() -eq '1') { $booted = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $booted) { throw "Android did not finish booting on $Serial" }
$package = & $AdbPath -s $Serial shell pm path ru.libappc
if ($LASTEXITCODE -ne 0 -or -not ($package -match 'package:')) {
    throw 'MangaLib package ru.libappc is not installed; its process need not run.'
}

$zip = [IO.Compression.ZipFile]::OpenRead($FixturePath)
$stage = Join-Path ([IO.Path]::GetTempPath()) ("readerlb-manga-seed-" + [guid]::NewGuid().ToString('N'))
try {
    foreach ($name in $required) {
        $entry = $zip.GetEntry($name)
        if ($null -eq $entry -or $entry.Length -eq 0) { throw "Fixture entry missing or empty: $name" }
    }
    $stagedTitle = Join-Path $stage $title
    New-Item -ItemType Directory -Path $stagedTitle -Force | Out-Null
    foreach ($name in $required) {
        $entry = $zip.GetEntry($name)
        $target = Join-Path $stagedTitle ([IO.Path]::GetFileName($name))
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
    }
    # The title paths below are fixed fixture names. No other user library folders are touched.
    Invoke-Adb @('root')
    Invoke-Adb @('wait-for-device')
    Invoke-Adb @('shell', 'mkdir', '-p', $root)
    Invoke-Adb @('shell', 'rm', '-rf', "$root/__readerlb_test__", "$root/$title")
    Invoke-Adb @('push', $stagedTitle, $root)
    foreach ($name in $required) {
        $remote = "$root/$title/$([IO.Path]::GetFileName($name))"
        Invoke-Adb @('shell', 'test', '-s', $remote)
    }
    Write-Output "Seeded $root/$title (info.json, chapters.json, v1-n1-990101.zip) on $Serial"
} finally {
    $zip.Dispose()
    $stageFull = [IO.Path]::GetFullPath($stage)
    $tempFull = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($stageFull.StartsWith($tempFull, [StringComparison]::OrdinalIgnoreCase) -and
        [IO.Path]::GetFileName($stageFull).StartsWith('readerlb-manga-seed-')) {
        Remove-Item -LiteralPath $stageFull -Recurse -Force -ErrorAction SilentlyContinue
    }
}
