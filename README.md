# Handy — Hand Gesture Recognition App

Handy is a real-time hand gesture recognition Android app built with MediaPipe and an on-device LLM. It detects hand gestures through the camera, builds a session of recognized signs, and uses AI to translate them into natural language — entirely on-device.

---

## Features

### Gesture Recognition
- Recognizes **13 hand gestures** in real-time using the front or back camera
- Built-in MediaPipe gestures: Closed Fist, Open Palm, Pointing Up, Thumb Up, Thumb Down, Victory, I Love You
- Custom landmark-based gestures: OK, Rock On, Call Me, Three, Four, Gun
- Color-coded hand skeleton overlay drawn live on the camera feed
- Haptic feedback on each committed gesture

### AI Translation (On-Device LLM)
- Translates a session of gesture tokens into a natural English sentence
- Detects the **intent** of the message (Question, Request, Greeting, etc.) shown as a badge
- Generates **3 suggested replies** — Formal, Casual, and Empathetic — as tappable chips
- Auto-translates 2 seconds after the last committed gesture
- All inference runs fully on-device using Gemma 3N via the MediaPipe LLM Inference API

### Gesture Suggestion
- Type any phrase and the LLM suggests which hand gestures to use to communicate it
- Output is rendered with full markdown support (bold, bullets, numbered lists)

### Session Management
- Committed gestures accumulate into a scrollable session
- Delete last word, reset full session, copy or share the session text
- Text-to-speech for both session text and selected reply chips
- Conversation history preserved across turns for context-aware translation

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Camera | CameraX |
| Gesture Recognition | MediaPipe Gesture Recognizer (tasks-vision 0.10.21) |
| Custom Gestures | MediaPipe Hand Landmarks (21 keypoints) |
| On-Device LLM | MediaPipe LLM Inference API (tasks-genai) |
| LLM Model | Gemma 3N E2B int4 |
| Architecture | ViewModel + StateFlow |
| Navigation | Jetpack Navigation Component |

---

## Setup

### Requirements
- Android 8.0+ (API 26+)
- ~2 GB free storage for the LLM model
- Physical device recommended (camera + performance)

### LLM Model

The model is not bundled in the APK. Push it to the device once before first launch:

```bash
adb push gemma-3n-E2B-it-int4.task /sdcard/Android/data/com.signapp/files/
```

The app checks both internal and external storage on startup and shows a progress indicator while the model loads.

### Build

1. Clone the repo
2. Open in Android Studio
3. Push the model file to the device (see above)
4. Run on a physical device

---

## Architecture

```
MainActivity
└── NavHostFragment
    ├── PermissionsFragment        camera permission gate
    └── CameraFragment             main screen
        ├── SignRecognizerHelper   MediaPipe gesture recognizer wrapper
        ├── CustomGestureDetector  landmark-based custom gesture logic
        ├── OverlayView            hand skeleton canvas overlay
        ├── MainViewModel          state — gestures, session, LLM phases
        └── LlmHelper              Gemma inference + prompt templates
```

**Gesture pipeline:**
1. CameraX frame → `SignRecognizerHelper.recognizeLiveStream()`
2. MediaPipe model classifies the gesture → if result is "None", `CustomGestureDetector` runs on the raw 21-point landmarks
3. Result must be stable for 4 consecutive frames before committing to the session
4. Auto-translate fires 2 seconds after the last commit

**LLM pipeline (two-pass):**
1. Pass 1 — translate gesture tokens + detect intent (combined prompt)
2. Pass 2 — generate 3 reply chips based on translated sentence + intent

---

## Gestures Reference

| Gesture | Hand Shape |
|---|---|
| Closed Fist | All fingers curled |
| Open Palm | All fingers extended |
| Pointing Up | Index finger extended upward |
| Thumb Up | Thumb extended upward |
| Thumb Down | Thumb extended downward |
| Victory | Index + middle extended (peace sign) |
| I Love You | Thumb + index + pinky extended (ASL ILY) |
| OK | Thumb + index pinched, middle/ring/pinky extended |
| Rock On | Index + pinky extended (devil horns) |
| Call Me | Thumb + pinky extended |
| Three | Index + middle + ring extended |
| Four | All four fingers extended, thumb folded |
| Gun | Index + thumb extended |

---

## Team

House Lannister — Built at a hackathon.