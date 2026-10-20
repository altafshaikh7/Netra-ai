# Netra AI — "See the world through sound"

Netra is an Android app designed for blind and low-vision users, keeping the phone camera active while walking, detecting surroundings in real time, and speaking prioritized audio alerts.

> **Important Limitation Note:**
> Netra assists with navigation and environmental awareness, but **it does not replace a white cane or guide dog**. Always exercise caution and use traditional mobility aids.

---

## Setup & Testing Instructions (Features 1 & 2)

### Prerequisites
- Android device running Android 8.0 (API 26) or higher.
- Camera and microphone permissions enabled.

### Features Implemented
1. **Camera Preview (Feature 1)**:
   - App opens straight to a high-contrast live camera preview with runtime permission requests (Camera, Microphone, Location) accompanied by spoken guidance.
2. **Voice Output / TTS Wrapper (Feature 2)**:
   - Text-to-Speech engine wrapper supporting queueing, interrupts, speed adjustments, and English / Hindi language toggling.
   - On first launch, announces: *"Netra ready. Note: this app assists but does not replace a white cane or guide."*

### How to Test on Your Phone
1. Connect your Android phone via USB with USB debugging enabled.
2. Run the app (`app:assembleDebug` / run from Android Studio).
3. On launch, listen to the audio announcement and review the high-contrast UI.
4. Grant camera and microphone permissions when prompted (or tap "Grant Permissions").
5. Tap the language toggle button ("हिंदी" / "English") to test English and Hindi voice announcements.
