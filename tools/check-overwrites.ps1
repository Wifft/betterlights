# Compares the vanilla copies in docs/vanilla with the Minecraft version in gradle.properties.
# Run after bumping minecraft_version and running `./gradlew genSources`:
#   pwsh -File tools/check-overwrites.ps1
# - docs/vanilla/*.java: methods replaced whole by an @Overwrite. Line 1 is
#   "// Source: <path in the sources jar> :: <start of the method declaration>".
#   If one differs, apply Mojang's change to the mod's copy, then refresh the file with -Update.
# - docs/vanilla/shaders/**: vanilla shaders the mod replaces in src/client/resources/assets/minecraft/shaders.
#   If one differs, redo the Betterlight edits on top of the new vanilla file.
param(
	# Rewrite docs/vanilla with the new vanilla code (after porting the changes)
	[switch]$Update
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$props = Get-Content (Join-Path $root 'gradle.properties')
$mcVersion = ($props | Where-Object { $_ -match '^minecraft_version=' }) -replace '^minecraft_version=', ''
$maven = Join-Path $root ".gradle/loom-cache/minecraftMaven/net/minecraft"

function Find-Jar([string]$pattern) {
	$jar = Get-ChildItem -Path $maven -Recurse -Filter $pattern -ErrorAction SilentlyContinue |
		Where-Object { $_.Directory.Name -eq $mcVersion } | Select-Object -First 1
	if (-not $jar) { throw "No $pattern for $mcVersion under $maven (run ./gradlew genSources first)" }
	return $jar.FullName
}

function Read-Entry([string[]]$jars, [string]$entryName) {
	foreach ($jarPath in $jars) {
		$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
		try {
			$entry = $zip.GetEntry($entryName)
			if ($entry) {
				$reader = New-Object System.IO.StreamReader($entry.Open())
				try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
			}
		} finally {
			$zip.Dispose()
		}
	}
	return $null
}

# A method runs from its declaration line (one tab of indent) to the next line that is exactly "\t}"
function Get-Method([string]$source, [string]$start) {
	$lines = $source -split "`r?`n"
	$out = New-Object System.Collections.Generic.List[string]
	$inside = $false
	foreach ($line in $lines) {
		if (-not $inside -and $line.StartsWith("`t$start")) { $inside = $true }
		if ($inside) {
			$out.Add($line)
			if ($line -eq "`t}") { return $out.ToArray() }
		}
	}
	return $null
}

$sourceJars = @(Find-Jar '*-common-*-sources.jar'; Find-Jar '*-clientOnly-*-sources.jar')
$clientJar = Get-ChildItem -Path $maven -Recurse -Filter "minecraft-clientOnly-*-$mcVersion.jar" |
	Where-Object { $_.Directory.Name -eq $mcVersion } | Select-Object -First 1
if (-not $clientJar) { throw "No client jar for $mcVersion under $maven" }

$changed = 0
Write-Host "Minecraft $mcVersion"

foreach ($file in Get-ChildItem (Join-Path $root 'docs/vanilla') -Filter '*.java') {
	$saved = Get-Content $file.FullName
	if ($saved[0] -notmatch '^// Source: (.+?) :: (.+)$') { throw "$($file.Name): line 1 must be '// Source: <path> :: <declaration start>'" }
	$path = $Matches[1]
	$start = $Matches[2]
	$header = $saved | Where-Object { $_ -match '^//' }
	$body = $saved | Where-Object { $_ -notmatch '^//' }

	$source = Read-Entry $sourceJars $path
	$current = if ($source) { Get-Method $source $start } else { $null }
	if (-not $current) {
		Write-Host "MISSING  $($file.Name): '$start' not found in $path" -ForegroundColor Red
		$changed++
		continue
	}

	$diff = Compare-Object $body $current -SyncWindow 0
	if ($diff) {
		Write-Host "CHANGED  $($file.Name)" -ForegroundColor Yellow
		$diff | ForEach-Object { Write-Host ("  {0} {1}" -f ($(if ($_.SideIndicator -eq '=>') { 'new' } else { 'old' })), $_.InputObject) }
		$changed++
		if ($Update) {
			$header[1] = "// Vanilla $mcVersion (Loom genSources). Overwritten by a Betterlight mixin."
			Set-Content -Path $file.FullName -Value (@($header) + $current) -Encoding utf8NoBOM
		}
	} else {
		Write-Host "same     $($file.Name)"
	}
}

$shaderRoot = Join-Path $root 'docs/vanilla/shaders'
foreach ($file in Get-ChildItem $shaderRoot -Recurse -File) {
	$relative = $file.FullName.Substring($shaderRoot.Length + 1).Replace('\', '/')
	$current = Read-Entry @($clientJar.FullName) "assets/minecraft/shaders/$relative"
	if ($null -eq $current) {
		Write-Host "MISSING  shaders/$relative" -ForegroundColor Red
		$changed++
		continue
	}

	$saved = [System.IO.File]::ReadAllText($file.FullName)
	if (($saved -replace "`r`n", "`n") -ne ($current -replace "`r`n", "`n")) {
		Write-Host "CHANGED  shaders/$relative (redo the Betterlight edits on top of the new file)" -ForegroundColor Yellow
		$changed++
		if ($Update) {
			[System.IO.File]::WriteAllText($file.FullName, $current)
		}
	} else {
		Write-Host "same     shaders/$relative"
	}
}

if ($changed -eq 0) {
	Write-Host "Nothing to port."
} else {
	Write-Host "$changed file(s) to review." -ForegroundColor Yellow
	exit 1
}
