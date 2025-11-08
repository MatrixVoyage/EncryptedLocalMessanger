# 📘 Encrypted Local Messenger - User Manual

**Welcome!** This guide will help you install, set up, and use the Encrypted Local Messenger application, even if you're not a technical person. Follow the step-by-step instructions below.

---

## 📑 Table of Contents

1. [What is This Application?](#what-is-this-application)
2. [System Requirements](#system-requirements)
3. [Installation Guide](#installation-guide)
4. [How to Use the Application](#how-to-use-the-application)
5. [Common Scenarios](#common-scenarios)
6. [Troubleshooting](#troubleshooting)
7. [Frequently Asked Questions (FAQ)](#frequently-asked-questions-faq)
8. [Tips for Best Experience](#tips-for-best-experience)

---

## 🤔 What is This Application?

**Encrypted Local Messenger** is a secure chat application that lets you:
- **Send messages** to people on the same Wi-Fi network or LAN
- **Share files** securely without using the internet
- **Keep conversations private** with military-grade encryption
- **Work offline** - no internet connection needed!

Think of it like WhatsApp or Messenger, but **completely private** and **works only on your local network** (like your office or home Wi-Fi).

### Why Use This?

✅ **Privacy**: Your messages never leave your local network  
✅ **Security**: All messages are encrypted (scrambled) so nobody can read them  
✅ **No Internet Required**: Perfect for places without internet or when you want complete privacy  
✅ **Free**: No subscriptions, no ads, no data collection  

---

## 💻 System Requirements

### What You Need:

- **Computer**: Windows, Mac, or Linux
- **Java**: Version 17 or higher (we'll show you how to check/install)
- **Network**: Wi-Fi or Ethernet connection (same network as other users)
- **Storage**: At least 100 MB free space

### Checking if You Have Java:

**Windows:**
1. Press `Windows + R` keys together
2. Type `cmd` and press Enter
3. Type `java -version` and press Enter
4. If you see version 17 or higher, you're good! ✅
5. If you see an error, you need to install Java (see below)

**Mac:**
1. Open "Terminal" (search for it in Spotlight)
2. Type `java -version` and press Enter
3. Check the version number

**Linux:**
1. Open Terminal
2. Type `java -version` and press Enter
3. Check the version number

### Installing Java (if needed):

**Option 1: Download from Official Site**
1. Visit: https://adoptium.net/
2. Click "Download" for your operating system
3. Run the installer
4. Restart your computer

**Option 2: Windows (Easy Way)**
1. Open Microsoft Store
2. Search for "OpenJDK"
3. Click "Get" to install

---

## 📥 Installation Guide

### Step 1: Download the Application

1. Go to the project folder or GitHub repository
2. Click the green "Code" button
3. Select "Download ZIP"
4. Save it to your computer (e.g., Desktop or Downloads)

### Step 2: Extract the Files

**Windows:**
1. Right-click the downloaded ZIP file
2. Select "Extract All..."
3. Choose a location (e.g., `C:\Users\YourName\EncryptedMessenger`)
4. Click "Extract"

**Mac:**
1. Double-click the ZIP file
2. It will automatically extract to the same folder

**Linux:**
1. Right-click the ZIP file
2. Select "Extract Here"

### Step 3: Build the Application

Now we need to prepare the application to run.

**Windows:**

1. Open the extracted folder
2. Hold `Shift` and right-click in an empty area
3. Select "Open PowerShell window here" (or "Open in Terminal")
4. Type this command and press Enter:
   ```
   .\gradlew.bat build
   ```
5. Wait 2-5 minutes (it will download required files)
6. You should see "BUILD SUCCESSFUL" ✅

**Mac/Linux:**

1. Open Terminal
2. Navigate to the extracted folder:
   ```bash
   cd ~/Downloads/EncryptedLocalMessenger
   ```
3. Make the script executable:
   ```bash
   chmod +x gradlew
   ```
4. Build the application:
   ```bash
   ./gradlew build
   ```
5. Wait for "BUILD SUCCESSFUL" ✅

---

## 🚀 How to Use the Application

### Starting the Application

**Windows:**

1. Open PowerShell in the application folder (same as Step 3 above)
2. Type this command:
   ```
   .\gradlew.bat run
   ```
3. A window will pop up! 🎉

**Mac/Linux:**

1. Open Terminal in the application folder
2. Type:
   ```bash
   ./gradlew run
   ```
3. A window will pop up! 🎉

### First-Time Setup

When you start the application, you'll see a dialog asking:

**"Choose how to run LocalChat:"**
- **Client** - Use this to connect to someone else's server
- **Server** - Use this to host a chat that others can join
- **Both** - Run both server and client (good for testing)

---

## 🎯 Common Scenarios

### Scenario 1: Setting Up a Chat Room (Server Mode)

**You are the "host" - others will connect to you.**

1. Start the application
2. Choose **"Server"**
3. You'll be asked for:
   - **Port Number**: Just press Enter (uses 5000)
   - **Password**: Enter a password (e.g., "MySecret123")
   - ⚠️ **Remember this password** - others need it to join!

4. A chat window opens showing:
   - "Listening on port 5000..."
   - Your network status

5. **Tell others your information:**
   - Your computer's IP address (see "Finding Your IP Address" below)
   - The port number (default: 5000)
   - The password you set

6. Wait for others to connect!

### Scenario 2: Joining a Chat Room (Client Mode)

**Someone else is hosting - you're joining their server.**

1. Start the application
2. Choose **"Client"**
3. You'll be asked for:
   - **Server Host**: Enter the host's IP address (e.g., 192.168.1.100)
   - **Port Number**: Enter the port (usually 5000)
   - **Password**: Enter the password the host gave you
   - **Your Name**: Enter your display name (e.g., "John")

4. Click OK
5. If everything is correct, you'll connect! ✅

### Scenario 3: Testing by Yourself

**Want to try it out alone?**

1. Start the application
2. Choose **"Both"**
3. Enter a port (5000) and password
4. Two windows will open - one server, one client
5. You can chat with yourself to test! 😊

---

## 📤 Sending Messages and Files

### Sending a Text Message

1. Type your message in the text box at the bottom
2. Click "Send" or press Enter
3. Your message appears on the right side (You)
4. Others see it on their screen as "Remote"

### Sending a File

1. Click the **"Send File"** button
2. Choose the file you want to send
3. Click "Open"
4. A progress bar shows the upload status
5. Others will receive the file automatically in their Downloads folder

### Receiving a File

1. When someone sends a file, you'll see:
   - "Incoming file: filename.jpg (1.5 MB)"
2. A progress bar shows the download
3. When complete: "Received file: filename.jpg (saved to C:\Users\YourName\Downloads)"
4. The file is automatically saved - no action needed!

---

## 🔍 Finding Your IP Address

### Windows:

**Method 1 (Easy):**
1. Press `Windows + R`
2. Type `cmd` and press Enter
3. Type `ipconfig` and press Enter
4. Look for "IPv4 Address" under your network adapter
5. It looks like: `192.168.1.100` or `10.0.0.50`

**Method 2 (Visual):**
1. Click the Wi-Fi icon in the taskbar
2. Click "Properties" under your network
3. Scroll down to "IPv4 address"

### Mac:

1. Click Apple menu → System Preferences
2. Click "Network"
3. Select your connection (Wi-Fi or Ethernet)
4. Your IP address is shown on the right

### Linux:

1. Open Terminal
2. Type `ip addr` or `ifconfig`
3. Look for "inet" followed by numbers like 192.168.1.100

---

## 🔧 Troubleshooting

### Problem: "BUILD FAILED" Error

**Solution:**
- Make sure Java is installed (version 17+)
- Check your internet connection (needed for first build)
- Try deleting the `.gradle` folder and build again

### Problem: Can't Connect to Server

**Checklist:**
- ✅ Are you on the same Wi-Fi network?
- ✅ Is the server running on the other computer?
- ✅ Did you enter the correct IP address?
- ✅ Did you enter the correct port number?
- ✅ Did you enter the correct password?
- ✅ Is firewall blocking the connection? (see below)

### Problem: Firewall Blocking Connection

**Windows:**
1. Go to Windows Security
2. Click "Firewall & network protection"
3. Click "Allow an app through firewall"
4. Find "Java" and make sure both Private and Public are checked ✅

**Mac:**
1. System Preferences → Security & Privacy
2. Click "Firewall" tab
3. Click lock icon and enter password
4. Click "Firewall Options"
5. Click "+" and add Java

### Problem: "Discovery Service Failed"

**Don't worry!** This is optional.
- You can still manually enter IP addresses to connect
- Discovery helps find others automatically, but it's not required

### Problem: File Transfer Failed

**Solutions:**
- Check available disk space
- Try a smaller file first
- Make sure connection is stable
- Ask sender to try again

---

## ❓ Frequently Asked Questions (FAQ)

### Q1: Do I need internet to use this?

**No!** You only need a local network (Wi-Fi or Ethernet). Internet is only needed for the first build to download dependencies.

### Q2: Can someone outside my network join?

**No.** This app only works on local networks for security and privacy.

### Q3: How secure is this?

Very secure! All messages are encrypted with military-grade AES-256-GCM encryption. Even if someone intercepts the messages, they can't read them without the password.

### Q4: What happens to my messages?

Messages are not saved anywhere. Once you close the application, all chat history is gone. This ensures privacy!

### Q5: Can I use this on multiple devices?

Yes! You can connect as many clients as you want to one server.

### Q6: What's the maximum file size I can send?

Theoretically unlimited, but very large files (>1 GB) may be slow. Recommended: Keep files under 500 MB.

### Q7: Do I need to rebuild every time?

No! Once you've built successfully, you can run the application with:
```
.\gradlew.bat run    (Windows)
./gradlew run        (Mac/Linux)
```

### Q8: Can I close the server window?

If you close the server window, all clients will disconnect. Keep the server window open as long as you want the chat room active.

### Q9: What if I forget the password?

The person running the server can restart it with a new password. All clients will need to reconnect with the new password.

### Q10: Is my chat history saved?

No, chat history is not saved. When you close the window, messages are gone. This is intentional for privacy.

---

## 💡 Tips for Best Experience

### For Server Hosts:

1. 📌 **Use a strong password**: Mix letters, numbers, and symbols
2. 🖥️ **Keep your computer awake**: If it sleeps, clients disconnect
3. 📝 **Write down your IP and password**: Share it with others easily
4. 🔌 **Use a wired connection**: More stable than Wi-Fi
5. 💪 **Run on a powerful computer**: Can handle more clients

### For Clients:

1. 📋 **Save connection details**: Keep IP, port, and password handy
2. ⚡ **Use Ethernet if possible**: Faster file transfers
3. 🔔 **Watch for notifications**: File transfer progress and errors
4. 💾 **Check Downloads folder**: Files are saved there automatically
5. 🔒 **Never share the password publicly**: Only with trusted users

### For Everyone:

1. 🎨 **Customize your name**: Make it easy for others to identify you
2. 📱 **Test first**: Try "Both" mode to understand how it works
3. 🤝 **Be patient**: First build takes a few minutes
4. 📶 **Check network**: Make sure you're on the same network
5. 🛡️ **Use strong passwords**: Especially in office environments

---

## 🆘 Still Need Help?

### Getting Support:

1. **Check this manual again** - Read the relevant section carefully
2. **Check the README.md** - More technical details available there
3. **Ask a tech-savvy friend** - Show them this manual
4. **Open an issue** - If you found a bug, report it on GitHub

### Before Asking for Help, Note Down:

- Your operating system (Windows 10, Mac OS, etc.)
- Java version (`java -version`)
- Error messages (take a screenshot)
- What you were trying to do
- What actually happened

---

## 🎉 Quick Start Checklist

Use this checklist for your first time:

**Before Starting:**
- [ ] Java 17+ installed
- [ ] Application downloaded and extracted
- [ ] Built successfully (`.\gradlew.bat build`)

**As Server:**
- [ ] Started application (`.\gradlew.bat run`)
- [ ] Chose "Server"
- [ ] Set port (5000) and password
- [ ] Found my IP address
- [ ] Shared IP, port, and password with others
- [ ] Server window shows "Listening..."

**As Client:**
- [ ] Got IP address, port, and password from server host
- [ ] Started application
- [ ] Chose "Client"
- [ ] Entered server details correctly
- [ ] Connected successfully!
- [ ] Can send messages ✅

---

## 📞 Contact & Support

If you're completely stuck and need help:

- **Email**: your.email@example.com
- **GitHub Issues**: Report bugs or request features
- **Documentation**: Check README.md for technical details

---

## 🎓 Learning More

Want to understand the technical side?

- Read the **README.md** for architecture details
- Check the **source code** - it's well-commented!
- Learn about **encryption** and why privacy matters
- Explore **network programming** concepts

---

<div align="center">

**Thank you for using Encrypted Local Messenger!**

🔐 Stay secure, stay private! 🔐

*This manual was written with ❤️ for non-technical users*

---

**Questions?** Don't hesitate to ask for help!

</div>
