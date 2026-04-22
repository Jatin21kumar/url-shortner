# Build
mvn clean package -DskipTests

# Create layer zip with forward-slash paths
Remove-Item -Force lambda-layer.zip -ErrorAction SilentlyContinue

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$layerZipPath = Join-Path (Resolve-Path ".").Path "lambda-layer.zip"
$layerZip = [System.IO.Compression.ZipFile]::Open($layerZipPath, [System.IO.Compression.ZipArchiveMode]::Create)

Get-ChildItem "target/dependency" -Filter "*.jar" | ForEach-Object {
    $entryName = "java/lib/" + $_.Name
    $entry = $layerZip.CreateEntry($entryName)
    $srcStream = [System.IO.File]::OpenRead($_.FullName)
    $dstStream = $entry.Open()
    $srcStream.CopyTo($dstStream)
    $srcStream.Close()
    $dstStream.Close()
}

$layerZip.Dispose()
Write-Host "Layer zip created with forward-slash paths"

# Extract compiled classes from JAR and repackage with forward-slash paths
Remove-Item -Recurse -Force lambda-classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path lambda-classes | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$jarPath = Resolve-Path "target/url-shortner-0.0.1-SNAPSHOT.jar"
$outZipPath = Resolve-Path "." | Join-Path -ChildPath "lambda-code.zip"
if (Test-Path $outZipPath) { Remove-Item $outZipPath }

$jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
$outZip = [System.IO.Compression.ZipFile]::Open($outZipPath, [System.IO.Compression.ZipArchiveMode]::Create)

foreach ($entry in $jar.Entries) {
    if ($entry.FullName.StartsWith("BOOT-INF/classes/") -and !$entry.FullName.EndsWith("/")) {
        # Strip BOOT-INF/classes/ prefix and ensure forward slashes
        $newName = $entry.FullName.Substring("BOOT-INF/classes/".Length)
        $newName = $newName.Replace("\", "/")

        $newEntry = $outZip.CreateEntry($newName)
        $srcStream = $entry.Open()
        $dstStream = $newEntry.Open()
        $srcStream.CopyTo($dstStream)
        $srcStream.Close()
        $dstStream.Close()
    }
}

$jar.Dispose()
$outZip.Dispose()

Write-Host "Done. lambda-layer.zip = dependencies, lambda-code.zip = compiled classes (forward-slash paths)"