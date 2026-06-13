# Handy — Sign Language Translator

Handy is an Android app that translates hand gestures into text in real time using your phone camera.

---

### How it works

Point your camera at your hand. Handy detects your gesture and types the word on screen instantly.

It uses Google's MediaPipe GestureRecognizer — a built-in AI that finds your hand, maps 21 landmark points on it, and identifies the sign. No internet required, runs fully on-device.

---

### Features

- Real-time gesture detection via front camera
- Builds a sentence word by word as you sign
- Stability filter — requires 4 consistent frames before committing a word (no junk)
- Edit tools: backspace, space, clear all
- Hand skeleton overlay drawn live on screen

---

### Gestures supported

| Gesture | Output |
|---|---|
| 👍 Thumbs Up | Thumb Up |
| 👎 Thumbs Down | Thumb Down |
| ✌️ Peace | Victory |
| ☝️ Pointing | Pointing Up |
| ✊ Fist | Closed Fist |
| 🖐️ Open Hand | Open Palm |
| 🤟 ILY | I Love You |

---

### Tech stack

- **Kotlin** — app language
- **CameraX** — camera capture
- **MediaPipe Tasks Vision** — gesture recognition AI
- **ConstraintLayout + ViewBinding** — UI

---

### Running locally

1. Clone the repo
2. Open in Android Studio
3. Connect your Android device
4. Run the app (`Shift + F10`)

Requires Android 7.0+ (API 24)