# NaNa

Offline, voice-controlled Android assistant by MercuReal Tech Solutions. Say "nana" then a command. Speech recognition (Vosk) and text-to-speech run on the phone. No INTERNET permission, and nothing leaves the device.

## Commands
- open <app>, launch <app>
- call <number> (spoken digits work: "call zero eight zero one two three")
- call <contact name>
- press back, go home, recents, open notifications
- read screen, scroll up, scroll down, tap <button text>
- what time is it, help, stop listening

## How it works
- NanaService is a foreground service (microphone type), so listening survives backgrounding.
- Vosk recognizes speech from the bundled model at app/src/main/assets/model (vosk-model-small-en-us-0.15).
- The wake word "nana" gates commands. Saying "nana" alone opens an 8 second window for the next phrase.
- CommandParser checks fixed phrases before regex rules, so "press back" never falls into the tap rule.
- NanaAccessibilityService performs global back/home/recents/notifications, reads visible text, taps and scrolls. Enable it once in Settings > Accessibility.
- Speaker uses the system offline TTS. The recognizer pauses while NaNa speaks.

## Build
Requirements: JDK 17 or 21, Android SDK with platform android-36 and build-tools 36.0.0 (or let Android Studio install them).

1. Put the Vosk model in app/src/main/assets/model. Download vosk-model-small-en-us-0.15 from https://alphacephei.com/vosk/models and unzip it so that the folder contents (am, conf, graph, ivector, README) sit directly in assets/model.
2. Set sdk.dir in local.properties, for example sdk.dir=/path/to/Android/Sdk.
3. Run ./gradlew :app:testDebugUnitTest :app:assembleDebug
4. Install app/build/outputs/apk/debug/app-debug.apk on a device running Android 10 (API 29) or newer.

Unit tests cover the command parser. The voice path needs a real phone to verify.

## License
MIT. See LICENSE.
