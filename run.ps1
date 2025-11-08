param(
  [ValidateSet('server','client')]
  [string]$mode = 'client'
)

$buildDir = "build"
$libsDir = Join-Path $buildDir "libs"
$flatlafJar = Join-Path $libsDir "FlatLaf.jar"

if (!(Test-Path $buildDir)) { Write-Host "Build output missing. Run .\build.ps1 first." -ForegroundColor Yellow; exit 1 }

if ($mode -eq 'server') {
  # Running from JAR uses manifest Class-Path
  if (Test-Path (Join-Path $buildDir 'EncryptedServer.jar')) {
    java -jar (Join-Path $buildDir 'EncryptedServer.jar')
  } else {
    # Run from classes; include FlatLaf if present
    $cp = "$buildDir"
    if (Test-Path $flatlafJar) { $cp = "$cp;$flatlafJar" }
    java -cp $cp EncryptedMultiServer
  }
} else {
  if (Test-Path (Join-Path $buildDir 'EncryptedClient.jar')) {
    java -jar (Join-Path $buildDir 'EncryptedClient.jar')
  } else {
    $cp = "$buildDir"
    if (Test-Path $flatlafJar) { $cp = "$cp;$flatlafJar" }
    java -cp $cp EncryptedClient
  }
}
