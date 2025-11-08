# PowerShell script to compile Java source files and build both Server & Client JARs

# Save this as build.ps1 in your project root (EncryptedLocalMessenger)

# Paths
$jdkPath = "C:\Program Files\Java\jdk-24\bin"  # adjust if needed
$srcDir = "src"
$buildDir = "build"
$libsDir = Join-Path $buildDir "libs"
$flatlafUrl = "https://repo1.maven.org/maven2/com/formdev/flatlaf/3.4.1/flatlaf-3.4.1.jar"
$flatlafJar = Join-Path $libsDir "FlatLaf.jar"
$slf4jApiUrl = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar"
$slf4jApiJar = Join-Path $libsDir "slf4j-api.jar"
$logbackClassicUrl = "https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.4.14/logback-classic-1.4.14.jar"
$logbackClassicJar = Join-Path $libsDir "logback-classic.jar"
$logbackCoreUrl = "https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.4.14/logback-core-1.4.14.jar"
$logbackCoreJar = Join-Path $libsDir "logback-core.jar"
$serverManifest = "manifest-server.txt"
$clientManifest = "manifest-client.txt"
$appManifest = "manifest-app.txt"

# Create build folder if it doesn't exist
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }
if (-not (Test-Path $libsDir)) { New-Item -ItemType Directory -Path $libsDir | Out-Null }

# Download FlatLaf if not present
if (-not (Test-Path $flatlafJar)) {
    Write-Host "Downloading FlatLaf..."
    try {
        Invoke-WebRequest -Uri $flatlafUrl -OutFile $flatlafJar -UseBasicParsing
    } catch {
        Write-Host "Failed to download FlatLaf: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Download SLF4J + Logback for logging
if (-not (Test-Path $slf4jApiJar)) {
    Write-Host "Downloading SLF4J API..."
    try {
        Invoke-WebRequest -Uri $slf4jApiUrl -OutFile $slf4jApiJar -UseBasicParsing
    } catch {
        Write-Host "Failed to download SLF4J API: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

if (-not (Test-Path $logbackClassicJar)) {
    Write-Host "Downloading Logback Classic..."
    try {
        Invoke-WebRequest -Uri $logbackClassicUrl -OutFile $logbackClassicJar -UseBasicParsing
    } catch {
        Write-Host "Failed to download Logback Classic: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

if (-not (Test-Path $logbackCoreJar)) {
    Write-Host "Downloading Logback Core..."
    try {
        Invoke-WebRequest -Uri $logbackCoreUrl -OutFile $logbackCoreJar -UseBasicParsing
    } catch {
        Write-Host "Failed to download Logback Core: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Compile Java source files
Write-Host "Compiling Java source files..."
$classPathEntries = @($buildDir, $flatlafJar, $slf4jApiJar, $logbackClassicJar, $logbackCoreJar) | Where-Object { $_ -and (Test-Path $_) }
$cp = [string]::Join(';', $classPathEntries)
& "$jdkPath\javac.exe" -cp $cp "$srcDir\*.java" -d $buildDir

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful!"

# Build JARs using manifest files
Write-Host "Building Server JAR..."
& "$jdkPath\jar.exe" cfm (Join-Path $buildDir "EncryptedServer.jar") $serverManifest -C $buildDir .

Write-Host "Building Client JAR..."
& "$jdkPath\jar.exe" cfm (Join-Path $buildDir "EncryptedClient.jar") $clientManifest -C $buildDir .

Write-Host "Building Unified App JAR..."
& "$jdkPath\jar.exe" cfm (Join-Path $buildDir "LocalChatApp.jar") $appManifest -C $buildDir .

Write-Host "Both JARs built successfully!" -ForegroundColor Green

# Prepare dist for single EXE packaging (without Launch4j step here)
$distApp = Join-Path "dist" "App"
if (-not (Test-Path $distApp)) { New-Item -ItemType Directory -Path $distApp | Out-Null }
if (-not (Test-Path (Join-Path $distApp "libs"))) { New-Item -ItemType Directory -Path (Join-Path $distApp "libs") | Out-Null }
Copy-Item (Join-Path $buildDir "LocalChatApp.jar") $distApp -Force
if (Test-Path $flatlafJar) { Copy-Item $flatlafJar (Join-Path $distApp "libs\FlatLaf.jar") -Force }
if (Test-Path $slf4jApiJar) { Copy-Item $slf4jApiJar (Join-Path $distApp "libs\slf4j-api.jar") -Force }
if (Test-Path $logbackClassicJar) { Copy-Item $logbackClassicJar (Join-Path $distApp "libs\logback-classic.jar") -Force }
if (Test-Path $logbackCoreJar) { Copy-Item $logbackCoreJar (Join-Path $distApp "libs\logback-core.jar") -Force }

Write-Host "Unified app assembled in dist/App (ready for Launch4j)." -ForegroundColor Green
