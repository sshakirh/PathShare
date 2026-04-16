# PathShare 🗺️

> Record your GPS route, share it with a 6-character code, and let friends follow your exact path with live turn-by-turn guidance.

[![Codemagic build status](https://api.codemagic.io/apps/YOUR_APP_ID/android-release/status_badge.svg)](https://codemagic.io/apps/YOUR_APP_ID/android-release/latest_build)

---

## Features

| Feature | Description |
|---|---|
| 🔴 **Record** | Live GPS tracking on Google Maps — polyline drawn in real-time |
| 💾 **Save** | Routes stored on-device, no account needed |
| 📤 **Share** | 6-character code uploaded to Firebase Firestore |
| 🔑 **Enter Code** | Friends type the code → route downloads instantly |
| ▶ **Follow** | Real-time guidance: grey = full route, green = completed, arrow = direction |
| 🎯 **Arrival Alert** | Vibration + banner on destination reached |
| 📋 **History** | View, re-share, or delete all saved routes |
| 🔗 **Deep Link** | `pathshare://route?code=XXXXXX` opens the app directly |

---

## Project Structure

```
PathShare/
├── codemagic.yaml                   ← CI/CD pipeline (3 workflows)
├── .github/workflows/android-ci.yml ← GitHub Actions (lint + unit tests)
├── .gitignore
├── gradle.properties
├── settings.gradle
├── build.gradle
└── app/
    ├── build.gradle                 ← Dependencies + signing config
    ├── proguard-rules.pro
    ├── google-services.json.example ← Template — replace with real file
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/pathshare/app/
        │   │   ├── activities/
        │   │   │   ├── MainActivity.java          ← Record + share screen
        │   │   │   ├── RoutePlaybackActivity.java ← Follow a shared route
        │   │   │   └── RouteHistoryActivity.java  ← All saved routes
        │   │   ├── services/
        │   │   │   └── LocationTrackingService.java ← Foreground GPS service
        │   │   ├── models/
        │   │   │   ├── Route.java
        │   │   │   └── RoutePoint.java
        │   │   └── utils/
        │   │       └── RouteRepository.java       ← Local + Firebase storage
        │   └── res/
        │       ├── layout/          ← All XML layouts
        │       ├── drawable/        ← Vector icons
        │       ├── values/          ← strings, colors, themes, dimens
        │       └── xml/             ← network_security_config
        ├── test/                    ← Unit tests (JUnit)
        └── androidTest/             ← Instrumented tests (Espresso)
```

---

## Quick Start

### 1. Clone & open in Android Studio

```bash
git clone https://github.com/YOUR_USERNAME/PathShare.git
cd PathShare
```

Open the folder in **Android Studio Hedgehog (2023.1.1)** or newer.

---

### 2. Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create / select a project
3. Enable: **Maps SDK for Android**
4. Create an API Key → restrict it to your package `com.pathshare.app` + your debug/release SHA-1
5. Open `gradle.properties` and replace:
   ```properties
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE
   ```

> **For CI:** add `MAPS_API_KEY` as an environment variable in Codemagic (see below).

---

### 3. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project → **Add Android app** → package: `com.pathshare.app`
3. Download `google-services.json` → place it in `app/google-services.json`
4. Enable **Cloud Firestore** (Start in test mode for development)
5. Recommended Firestore security rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /shared_routes/{code} {
      allow read: if true;               // anyone can read a shared route
      allow write: if request.auth != null;  // only authenticated users write
    }
  }
}
```

> **For CI:** base64-encode your `google-services.json` and store it as `GOOGLE_SERVICES_JSON` in Codemagic environment variables.

```bash
# Generate the base64 string to paste into Codemagic:
base64 -i app/google-services.json | pbcopy   # macOS
base64 -w 0 app/google-services.json          # Linux
```

---

## GitHub Setup

### Push to GitHub

```bash
git init
git add .
git commit -m "Initial commit: PathShare Android app"
git remote add origin https://github.com/YOUR_USERNAME/PathShare.git
git branch -M main
git push -u origin main
```

### Recommended branch strategy

```
main          ← stable, triggers release build in Codemagic
develop       ← integration branch, triggers debug build
feature/*     ← feature branches, triggers PR check
release/x.y   ← release candidates
```

---

## Codemagic CI/CD Setup

### Step 1 — Connect your repository

1. Sign in at [codemagic.io](https://codemagic.io) with GitHub
2. Click **Add application** → select `PathShare`
3. Codemagic auto-detects `codemagic.yaml` ✅

### Step 2 — Create environment variable group

In Codemagic → **Teams → Global variables** (or app-level):

Create a group named **`pathshare_env`** with these variables:

| Variable | Value | Secret? |
|---|---|---|
| `MAPS_API_KEY` | Your Google Maps API key | ✅ Yes |
| `GOOGLE_SERVICES_JSON` | Base64-encoded `google-services.json` | ✅ Yes |
| `EMAIL_RECIPIENTS` | your@email.com | No |

### Step 3 — Add keystore (for release signing)

1. Codemagic → App settings → **Code signing** → **Android** → **Add keystore**
2. Upload your `.jks` or `.keystore` file
3. Name it **`pathshare_keystore`** (must match `codemagic.yaml`)
4. Enter key alias, key password, store password

> **Create a new keystore** if you don't have one:
> ```bash
> keytool -genkey -v -keystore pathshare.keystore \
>   -alias pathshare -keyalg RSA -keysize 2048 -validity 10000
> ```
> Keep this file safe — you need the same keystore to update your app on Play Store forever.

### Step 4 — Workflows overview

| Workflow | Trigger | Output |
|---|---|---|
| `android-debug` | Push to **any branch** | Debug APK |
| `android-release` | Push to `main` or tag `v*` | Signed APK + AAB |
| `android-pr-check` | Pull Request | Lint + test report only |

### Step 5 — Tag a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

Codemagic picks up the tag → builds a signed release AAB ready for Play Store. 🚀

---

## Publishing to Google Play (optional)

Uncomment the `google_play` section in `codemagic.yaml`:

```yaml
publishing:
  google_play:
    credentials: $GPLAY_SERVICE_ACCOUNT_JSON
    track: internal
    submit_as_draft: true
```

Add `GPLAY_SERVICE_ACCOUNT_JSON` (base64-encoded service account JSON) to Codemagic environment variables.

---

## Running Locally

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Unit tests
./gradlew test

# Lint
./gradlew lint
```

---

## App Permissions Explained

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Precise GPS for route recording |
| `ACCESS_BACKGROUND_LOCATION` | Continue recording when screen is off |
| `FOREGROUND_SERVICE_LOCATION` | Android 14+ requirement for foreground location service |
| `INTERNET` | Google Maps tiles + Firebase sync |
| `VIBRATE` | Haptic feedback on destination arrival |

---

## Environment Variables Reference

| Variable | Where used | Description |
|---|---|---|
| `MAPS_API_KEY` | `gradle.properties` / Codemagic | Google Maps Android API key |
| `GOOGLE_SERVICES_JSON` | `codemagic.yaml` script | Base64 Firebase config |
| `CM_KEYSTORE_PATH` | Set by Codemagic automatically | Path to uploaded keystore |
| `CM_KEY_ALIAS` | Set by Codemagic automatically | Key alias |
| `CM_KEY_PASSWORD` | Set by Codemagic automatically | Key password |
| `CM_STORE_PASSWORD` | Set by Codemagic automatically | Store password |
| `EMAIL_RECIPIENTS` | `codemagic.yaml` | Build notification email(s) |

---

## Tech Stack

- **Language:** Java 17
- **Min SDK:** 24 (Android 7.0) · **Target SDK:** 34 (Android 14)
- **Google Maps SDK:** 18.2.0
- **Google Location:** 21.2.0
- **Firebase Firestore:** BOM 32.8.0
- **Gson:** 2.10.1
- **Material Components:** 1.11.0
- **CI/CD:** Codemagic (`codemagic.yaml`)

---

## License

MIT © 2024 PathShare
