param([string]$Jar)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path $PSScriptRoot -Parent
if (-not $Jar) {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $repo 'build/libs') -Filter 'corgikubejscompat-*.jar')
    if ($jars.Count -ne 1) { throw 'Expected exactly one release jar' }
    $Jar = $jars[0].FullName
}
$archive = [IO.Compression.ZipFile]::OpenRead($Jar)
try {
    $classes = @($archive.Entries | Where-Object FullName -like '*.class' | ForEach-Object FullName | Sort-Object)
    $expected = @(
        'io/github/dirtyredz/corgikubejscompat/CorgiKubeJSCompat.class',
        'io/github/dirtyredz/corgikubejscompat/CorgiRegistryPlugin.class'
    )
    if (Compare-Object $classes $expected) { throw "Unexpected release classes: $classes" }
    function Read-Entry([string]$Name) {
        $entry = $archive.GetEntry($Name)
        if (-not $entry) { throw "Missing $Name" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    $plugin = Read-Entry 'kubejs.plugins.txt'
    if ($plugin.Trim() -cne 'io.github.dirtyredz.corgikubejscompat.CorgiRegistryPlugin') {
        throw 'Plugin discovery must contain exactly the BOTH-side plugin name'
    }
    $toml = Read-Entry 'META-INF/mods.toml'
    $versions = @{kubejs='[2001.6.5-build.26]'; corgilib='[4.0.3.5]'}
    foreach ($mod in $versions.Keys) {
        $blocks = @($toml -split '\[\[dependencies\.corgikubejscompat\]\]' |
            Where-Object { $_ -match ('(?m)^modId="' + $mod + '"\s*$') })
        if ($blocks.Count -ne 1) { throw "Missing or repeated dependency $mod" }
        $block = $blocks[0]
        foreach ($required in @('mandatory=true', 'side="BOTH"', ('versionRange="' + $versions[$mod] + '"'))) {
            if (-not $block.Contains($required)) { throw "$mod missing $required" }
        }
    }
    $versionLine = Get-Content (Join-Path $repo 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }
    $version = $versionLine.Substring('mod_version='.Length)
    if (-not $toml.Contains('version="' + $version + '"')) { throw 'Unexpanded or incorrect mod version' }
    Write-Output 'PASS: exact BOTH dependencies, plugin entry, two production classes, no tests or bundled dependencies'
} finally { $archive.Dispose() }
Get-FileHash -LiteralPath $Jar -Algorithm SHA256 | Format-List
