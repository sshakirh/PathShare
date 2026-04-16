# PathShare – Android App

Record your GPS route, share it with friends, and guide them along the exact same path.

---

## Features

| Feature | Description |
|---|---|
| 🔴 Record Route | Live GPS tracking on Google Maps with real-time polyline drawing |
| 📊 Live Stats | Distance and duration shown while recording |
| 💾 Save Locally | All routes stored on-device (no login required for basic use) |
| 📤 Share Code | Upload route to Firebase → generate a 6-char code |
| 🔑 Enter Code | Friends enter the code → route downloads to their device |
| ▶ Follow Route | Real-time guidance along the recorded path with turn-by-turn direction arrows |
| 🎯 Arrival Alert | Vibration + toast when destination is reached |
| 📋 Route History | View, share, or delete all saved routes |
| 🔗 Deep Link | `pathshare://route?code=XXXXXX` opens the app directly |

---

## Project Structure

```
PathShareApp/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/pathshare/app/
│   │   ├── activities/
│   │   │   ├── MainActivity.java          ← Record + share screen
│   │   │   ├── RoutePlaybackActivity.java ← Follow a shared route
│   │   │   └── RouteHistoryActivity.java  ← All saved routes
│   │   ├── services/
│   │   │   └── LocationTrackingService.java ← Foreground GPS service
│   │   ├── models/
│   │   │   ├── Route.java                 ← Route data model
│   │   │   └── RoutePoint.java            ← Single GPS waypoint
│   │   └── utils/
│   │       └── RouteRepository.java       ← Local + Firebase storage
│   └── res/layout/
│       ├── activity_main.xml
│       ├── activity_route_playback.xml
│       ├── activity_route_history.xml
│       └── item_route.xml
```

---

## Setup Instructions

### 1. Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create / select a project
3. Enable **Maps SDK for Android** and **Places API**
4. Create an API key (restrict it to your app's package + SHA-1)
5. Open `app/build.gradle` and replace:
   ```groovy
   manifestPlaceholders = [MAPS_API_KEY: "YOUR_GOOGLE_MAPS_API_KEY"]
   ```

### 2. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project
3. Add an **Android** app with package `com.pathshare.app`
4. Download `google-services.json` and place it in the `app/` folder
5. Enable **Firestore Database** in the Firebase console
6. Set Firestore rules (for development):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /shared_routes/{code} {
         allow read, write: if true;
       }
     }
   }
   ```
   > ⚠️ Tighten these rules before production!

### 3. Drawable Resources Needed

Add these to `app/src/main/res/drawable/`:

- `ic_launcher.xml` (or PNG) – app icon
- `ic_record.xml` – small icon for the notification (24×24dp white vector)

Example `ic_record.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#FFFFFF" android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10-4.48 10-10S17.52,2 12,2z"/>
</vector>
```

### 4. Build & Run

```bash
# From Android Studio: open PathShareApp folder → Run on device/emulator
# Or from CLI:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## How It Works

### Recording a Route
1. Tap **Start Recording** → name your route
2. Walk/drive — GPS points are saved every 3 seconds (filtered to ≤30 m accuracy)
3. A blue polyline draws live on the map
4. Tap **Stop & Save** → route saved locally

### Sharing
1. After stopping, tap **Share Code** (or open History → share)
2. Route uploads to Firebase Firestore
3. A 6-character code is generated (e.g. `K7PQ2R`)
4. Share via WhatsApp, SMS, any app

### Following (Friends)
1. Friend opens PathShare → taps **Enter Code**
2. Types the 6-char code → route downloads
3. Taps **Start Following**
4. App updates live:
   - **Grey line** = full recorded route
   - **Green line** = portion already covered
   - **Arrow + direction** = turn instruction (straight / left / right / etc.)
   - **Progress bar** = how far along the route
5. On arrival: vibration + success message

---

## Permissions

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS tracking |
| `ACCESS_BACKGROUND_LOCATION` | Continue recording when screen off |
| `FOREGROUND_SERVICE_LOCATION` | Required for Android 14+ foreground location |
| `INTERNET` | Google Maps tiles + Firebase |
| `VIBRATE` | Arrival alert |

---

## Customisation Tips

- **Tracking interval**: change `INTERVAL_MS` in `LocationTrackingService.java` (default 3000 ms)
- **Accuracy filter**: change `MIN_ACCURACY` (default 30 m — increase for urban canyons)
- **Arrival distance**: change `ARRIVAL_THRESHOLD` in `RoutePlaybackActivity.java` (default 25 m)
- **Map style**: add a `map_style.json` file and call `map.setMapStyle(...)` for a custom look
- **Route expiry**: add a `createdAt` Firestore field and a Cloud Function to delete old routes

---

## Dependencies

```
Google Maps SDK for Android  18.2.0
Google Location Services      21.1.0
Firebase Firestore            (BOM 32.7.0)
Gson                          2.10.1
Material Components           1.11.0
Room (local DB)               2.6.1
```
