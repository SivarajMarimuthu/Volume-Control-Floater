# Floating Volume Bubble (Android)

A lightweight Android accessibility tool that provides a **floating volume bubble** similar to the Quick Ball on MIUI.  
With simple taps, you can instantly control system volume, mute/unmute, and quickly access settings.

---

## 🚀 Features

### 🎧 Volume Controls
- **Single Tap → Show System Volume Panel**
- **Double Tap → Toggle Mute / Unmute**
- Uses Android's **default system volume slider** (no custom UI).

### ⚪ Floating Bubble
- Smooth draggable floating bubble (overlay)
- Automatically hides itself when inactive
- Snaps to nearest screen edge (Quick Ball style)
- Adjustable bubble size (Small / Medium / Large)
- Adapts to dark mode

### ⚙️ Long Press Actions
- **Long Press → Open Settings Page**
- Settings allow:
  - Change bubble size
  - Hide or show the app icon in the launcher

### 🛠 Background Service
- Runs as a foreground overlay service to avoid being killed
- Optionally supports **stealth notification mode** (hidden/minimal)

---

## 📝 Requirements

- Android **8.0 (Oreo)** or later
- Overlay permission (`SYSTEM_ALERT_WINDOW`)
- Foreground service permission
- Notification permission (Android 13+)

---

## 📌 Permissions Used

| Permission | Purpose |
|-----------|----------|
| `SYSTEM_ALERT_WINDOW` | Draw the floating bubble over apps |
| `FOREGROUND_SERVICE` | Keep the service alive in the background |
| `POST_NOTIFICATIONS` | Required for foreground service on Android 13+ |

---

## 📂 Project Structure
app/
├── java/com.example.volumecontrol/
│ ├── MainActivity.java
│ ├── SettingsActivity.java
│ ├── FloatingService.java
│ ├── LauncherAlias.java
│
├── res/
│ ├── layout/floating_bubble.xml
│ ├── layout/settings_activity.xml
│ ├── drawable/bubble_bg.xml
│ ├── mipmap/ic_volume.png
│
├── AndroidManifest.xml


---

## 🧩 Key Components

### **FloatingService**
- Creates and manages the floating bubble.
- Handles dragging, snapping, auto-hide, taps, and long-press detection.
- Shows system volume panel using `AudioManager`.

### **SettingsActivity**
- Lets user choose bubble size.
- Lets user hide/show the launcher icon using `PackageManager`.

### **LauncherAlias**
- Hidden launcher entry that can be toggled on/off.

---

## 🛠 How to Build

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/VolumeBubble.git

---

## 🧩 Key Components

### **FloatingService**
- Creates and manages the floating bubble.
- Handles dragging, snapping, auto-hide, taps, and long-press detection.
- Shows system volume panel using `AudioManager`.

### **SettingsActivity**
- Lets user choose bubble size.
- Lets user hide/show the launcher icon using `PackageManager`.

### **LauncherAlias**
- Hidden launcher entry that can be toggled on/off.

---

## 🛠 How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/VolumeBubble.git
2. Open in Android Studio.

3. Build & run on a real device.

4. Grant overlay permission when asked.

5. Start the bubble service from the main screen.
