# 🔐 Encrypted Local Messenger

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Gradle](https://img.shields.io/badge/Gradle-8.10.2-02303A?style=for-the-badge&logo=gradle)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
![Security](https://img.shields.io/badge/Security-AES--GCM-red?style=for-the-badge&logo=security)

**A military-grade encrypted local area network (LAN) chat application with zero internet dependency**

[Features](#-key-features) • [Tech Stack](#-tech-stack) • [Quick Start](#-quick-start) • [Security](#-security-architecture) • [Screenshots](#-screenshots)

</div>

---

## 📖 Overview

**Encrypted Local Messenger** is a sophisticated peer-to-peer communication platform designed for secure, privacy-first messaging within local networks. Unlike traditional chat applications that rely on cloud servers and internet connectivity, this application operates entirely offline, making it perfect for organizations requiring air-gapped communication, secure file transfers, or simply valuing absolute privacy.

### 🎯 What Makes This Unique?

- **🔒 Military-Grade Encryption**: End-to-end AES-GCM encryption with password-based key derivation (PBKDF2)
- **🌐 Zero Internet Dependency**: Complete offline operation - your data never leaves your LAN
- **🔍 Auto-Discovery**: Automatic peer discovery via UDP broadcasting - no manual IP configuration needed
- **📁 Secure File Transfer**: Encrypted file sharing with SHA-256 integrity verification and chunked streaming
- **💬 Real-Time Communication**: Multi-client support with instant message relay and connection pooling
- **🎨 Modern UI**: Beautiful FlatLaf-based interface with dark/light theme support
- **📱 Flexible Deployment**: Run as server, client, or both simultaneously
- **🔧 Bluetooth Support**: Alternative wireless communication channel (experimental)
- **📊 Comprehensive Logging**: SLF4J/Logback integration for production-grade monitoring
- **🚀 Production Ready**: Thread-safe architecture with heartbeat monitoring and graceful shutdown

---

## 🛠️ Tech Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 (compiled to 17) | Core runtime & application logic |
| **Gradle** | 8.10.2 | Build automation & dependency management |
| **Swing** | Built-in | Desktop GUI framework |
| **FlatLaf** | 2.6 | Modern look-and-feel for UI |

### Security & Cryptography

| Component | Purpose |
|-----------|---------|
| **AES-GCM** | Authenticated encryption with associated data |
| **PBKDF2** | Password-based key derivation function |
| **SHA-256** | File integrity verification |
| **Bouncy Castle** | Cryptographic provider (runtime) |

### Networking & Communication

| Protocol/Library | Purpose |
|------------------|---------|
| **TCP Sockets** | Reliable message transmission |
| **UDP Broadcasting** | LAN peer discovery |
| **Bluetooth RFCOMM** | Alternative wireless communication |
| **Custom Protocol** | Chunked file transfer with metadata |

### Development & Quality

| Tool | Purpose |
|------|---------|
| **JUnit 5** | Unit testing framework |
| **Mockito** | Mocking & test utilities |
| **SLF4J + Logback** | Structured logging |
| **Launch4j** | Native Windows executable generation |

---

## ✨ Key Features

### 🔐 Security First

- **End-to-End Encryption**: Every message and file is encrypted using AES-256-GCM before transmission
- **Perfect Forward Secrecy**: Each message uses a unique nonce/IV - replay attacks are impossible
- **Password Protection**: PBKDF2 key derivation with configurable iterations
- **Integrity Verification**: AEAD tags ensure message authenticity and detect tampering
- **No Data Leakage**: All communication stays within your local network

### 🌐 Networking Excellence

- **Smart Discovery**: Automatic peer detection with username broadcasting
- **Invitation System**: Send direct connection invites to discovered peers
- **Multi-Client Architecture**: Server supports unlimited concurrent connections
- **Connection Pooling**: Thread pool executor for efficient resource management
- **Heartbeat Monitoring**: Automatic detection and cleanup of dead connections
- **Graceful Degradation**: Continues operating even if discovery service fails

### 📁 Advanced File Transfer

- **Chunked Streaming**: Efficient transfer of large files with progress tracking
- **SHA-256 Verification**: Automatic integrity checking of received files
- **Resume Capability**: Sequence-based protocol allows future resume implementation
- **Metadata Support**: Original filename and size preservation
- **Collision Handling**: Automatic renaming of duplicate files
- **Cancellation Support**: User-initiated transfer abortion

### 🎨 User Experience

- **Intuitive Interface**: Clean, modern design with status indicators
- **Peer List**: Real-time view of discovered users on the network
- **Connection Status**: Visual feedback for connection state and peer count
- **Theme Support**: Light/dark mode and system preference detection
- **Preferences Dialog**: Easy configuration of username and network settings
- **Progress Tracking**: Real-time upload/download progress bars

### 🏗️ Architecture Highlights

- **Thread-Safe Design**: Concurrent collections and proper synchronization
- **Resource Management**: Automatic cleanup and shutdown hooks
- **Error Handling**: Comprehensive exception handling with user feedback
- **Logging**: Structured logging with configurable levels and file rotation
- **Modular Design**: Clean separation of concerns (UI, network, crypto, protocol)
- **Testable**: Unit tests for critical components (encryption, protocol, server)

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (Java 21 recommended)
- **Windows/Linux/macOS** with network access
- **Gradle** (or use included wrapper)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/encrypted-local-messenger.git
   cd encrypted-local-messenger
   ```

2. **Build the project**
   ```bash
   # Windows
   .\gradlew.bat build

   # Linux/macOS
   ./gradlew build
   ```

3. **Run the application**
   ```bash
   # Windows
   .\gradlew.bat run

   # Linux/macOS
   ./gradlew run
   ```

### 🎯 Usage Examples

#### Starting a Server

```bash
# Using Gradle
.\gradlew.bat run

# Or using PowerShell script
.\run.ps1 -mode server
```

When prompted:
- **Port**: Choose any available port (default: 5000)
- **Password**: Set a strong shared password

#### Connecting as Client

```bash
# Using Gradle
.\gradlew.bat run

# Or using PowerShell script
.\run.ps1 -mode client
```

When prompted:
- **Host**: Server IP address (or use LAN discovery)
- **Port**: Server port (default: 5000)
- **Password**: Must match server password
- **Username**: Your display name for discovery

#### Running Both (Testing)

The launcher provides a "Both" option that starts a server and automatically connects a client to `localhost` - perfect for testing!

---

## 🔒 Security Architecture

### Encryption Flow

```
Plaintext → PBKDF2(password) → AES-256-GCM + Random IV → Base64 → Network
Network → Base64 Decode → AES-256-GCM Verify & Decrypt → Plaintext
```

### Key Features

1. **Key Derivation**: PBKDF2-HMAC-SHA256 with 65,536 iterations
2. **Cipher**: AES-256 in Galois/Counter Mode (AEAD)
3. **IV Generation**: Cryptographically secure random 12-byte nonce per message
4. **Authentication**: 128-bit authentication tag prevents tampering
5. **Encoding**: Base64 for safe text transmission

### File Transfer Security

1. **Header**: Filename, size, and SHA-256 hash transmitted first
2. **Chunks**: File split into 8KB encrypted chunks with sequence numbers
3. **Verification**: Receiver computes SHA-256 and validates against header
4. **Integrity**: Any mismatch aborts transfer and deletes partial file

---

## 📁 Project Structure

```
EncryptedLocalMessenger/
├── src/                          # Source code
│   ├── LocalChatApp.java         # Main application launcher
│   ├── EncryptedMultiServer.java # Server implementation
│   ├── EncryptedClient.java      # Client implementation
│   ├── ChatWindow.java           # GUI interface
│   ├── EncryptionUtil.java       # AES-GCM encryption
│   ├── FileTransferProtocol.java # File transfer protocol
│   ├── DiscoveryService.java     # UDP peer discovery
│   ├── ClientHandler.java        # Server-side client connection handler
│   ├── PrefsManager.java         # User preferences storage
│   ├── AppLogger.java            # Logging configuration
│   └── main/resources/           # Resources (logback config)
├── testsrc/                      # Unit tests
│   ├── EncryptionUtilTest.java
│   ├── FileTransferProtocolTest.java
│   └── EncryptedMultiServerTest.java
├── gradle/                       # Gradle wrapper files
├── launch4j/                     # Windows EXE configuration
├── build.gradle                  # Build configuration
├── gradlew / gradlew.bat         # Gradle wrapper scripts
└── README.md                     # This file
```

---

## 🧪 Testing

Run the comprehensive test suite:

```bash
# Run all tests
.\gradlew.bat test

# Run with coverage
.\gradlew.bat test jacocoTestReport

# Run specific test
.\gradlew.bat test --tests EncryptionUtilTest
```

### Test Coverage

- ✅ Encryption/Decryption correctness
- ✅ File transfer protocol parsing
- ✅ Server multi-client handling
- ✅ Message integrity verification
- ✅ Error handling and edge cases

---

## 🔧 Advanced Configuration

### Logging Configuration

Edit `src/main/resources/logback.xml` to customize logging behavior:

```xml
<logger name="com.localchat" level="DEBUG"/>
<appender name="FILE" class="ch.qos.logback.core.FileAppender">
    <file>logs/app.log</file>
</appender>
```

### Network Tuning

Adjust these constants in the source code:

- **Discovery Port**: `DiscoveryService.DISCOVERY_PORT` (default: 8888)
- **Heartbeat Interval**: `HEARTBEAT_INTERVAL_MS` (default: 30s)
- **Chunk Size**: `FileTransferProtocol.CHUNK_SIZE` (default: 8192 bytes)
- **Client Thread Pool**: `createClientExecutor()` in `EncryptedMultiServer`

### Building Native Executables

Create a Windows executable:

```bash
# Build JAR first
.\gradlew.bat shadowJar

# Use Launch4j with provided configuration
# Output: dist/LocalChatApp.exe
```

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Guidelines

- Follow existing code style and naming conventions
- Add unit tests for new features
- Update documentation for API changes
- Ensure all tests pass before submitting PR
- Use meaningful commit messages

---

## 🐛 Known Issues & Roadmap

### Known Issues

- Bluetooth implementation is experimental and may require additional configuration
- Large file transfers (>1GB) may consume significant memory
- Discovery service may not work on networks with strict firewall rules

### Roadmap

- [ ] Add file transfer resume capability
- [ ] Implement group chat rooms
- [ ] Add support for voice/video calls
- [ ] Create mobile client (Android/iOS)
- [ ] Add message history persistence
- [ ] Implement user authentication system
- [ ] Add support for custom plugins
- [ ] Create web-based admin panel

---

## 📜 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **FlatLaf** - Modern Swing look and feel
- **Bouncy Castle** - Cryptographic provider
- **SLF4J/Logback** - Logging framework
- **JUnit & Mockito** - Testing frameworks

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/encrypted-local-messenger/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/encrypted-local-messenger/discussions)
- **Email**: your.email@example.com

---

## ⚠️ Disclaimer

This software is provided for educational and legitimate business purposes only. Users are responsible for compliance with local laws and regulations regarding encryption and network security. The authors assume no liability for misuse of this software.

---

<div align="center">

**Made with ❤️ and ☕ for privacy-conscious developers**

⭐ Star this repo if you find it useful!

[Report Bug](https://github.com/yourusername/encrypted-local-messenger/issues) • [Request Feature](https://github.com/yourusername/encrypted-local-messenger/issues)

</div>