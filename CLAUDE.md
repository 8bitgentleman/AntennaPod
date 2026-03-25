# AntennaPod — Claude Notes

## Environment

Requires **JDK 21** via SDKMAN. To activate it in a shell before running Gradle:

```bash
export SDKMAN_DIR="/Users/mtvogel/.sdkman"
source "/Users/mtvogel/.sdkman/bin/sdkman-init.sh" 2>/dev/null
sdk env
```

If Homebrew's JDK is hardcoded in `~/.zshrc` (e.g. `export JAVA_HOME=".../openjdk@17/..."`), remove those lines — SDKMAN must own `JAVA_HOME`.

---

## Build & Deploy

Always build and install release, not debug:

```bash
# Build
./gradlew assembleRelease   # or assemblePlayRelease / assembleFreeRelease

# Install to connected device
adb install -r app/build/outputs/apk/play/release/*.apk

# Verify installed
adb shell pm list packages | grep de.danoeh.antennapod
```

Before any PR, run style checks:
```bash
./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug
```

---

## ProGuard / R8 Rules for JNI

R8 will strip native JNI classes unless explicitly kept. Any class with a `native` method or loaded via `System.loadLibrary` needs a keep rule in `app/proguard-rules.pro`:

```
-keep class de.danoeh.antennapod.feature.highlight.WhisperTranscriptionService { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
```

This bit us before — debug builds work fine but release crashes because R8 strips the JNI bridge class.

---

## Architecture

New features go in their own Gradle module under `feature/`. See `:feature:highlight` as a reference.

| Concern | Pattern |
|---|---|
| HTTP | OkHttp3 (same as `GpodnetService`) |
| JSON | `org.json.JSONObject` / `JSONArray` — no Gson/Moshi |
| Database | Manual SQLite via `PodDBAdapter` singleton; `DBReader`/`DBWriter` static methods |
| Threading | `DBWriter` internal `dbExec` ExecutorService; UI via RxJava3 `Schedulers.io()` → `AndroidSchedulers.mainThread()` |
| Events | EventBus (`org.greenrobot.eventbus`) for cross-component signals |
| View binding | Enabled globally in `common.gradle`; binding class auto-generated from layout XML name |
| Preferences nav | `PreferenceActivity.openScreen(R.xml.xxx)` from `MainPreferencesFragment` |

New feature modules need `apply from: "../../playFlavor.gradle"` in their `build.gradle` — without it, Gradle can't resolve `free`/`play` flavor variants from `:storage:database` and other deps.

---

## Highlight Feature (`feature/highlight`)

Snipd-like capture: tap a button while listening → transcribe last N seconds on-device → edit → save to local DB and sync to Readwise/Roam.

**Capture flow**
1. Long-press play button in `AudioPlayerFragment` → `showCaptureSheet()` opens `CaptureBottomSheet`
2. User adjusts lookback window and taps Transcribe
3. `HighlightCaptureViewModel` calls `AudioCaptureHelper` to decode the audio segment from the episode file to a WAV
4. `WhisperTranscriptionService` runs the WAV through Sherpa-ONNX / Moonshine v2 (model lives in app storage, downloaded on first use via `ModelDownloadManager`)
5. Transcript is shown in the edit sheet (`HighlightEditBottomSheet`); user saves → `DBWriter.addPodcastHighlight()`

**Sync flow**
- **Readwise**: `ReadwiseSyncService` POSTs to Readwise API v3. Sync is also scheduled as a background `WorkManager` job via `ReadwiseSyncWorker` (retries on failure).
- **Roam**: `RoamSyncService` POSTs to the Roam Local API. Sync is fire-and-forget from the ViewModel (no worker needed — requires Roam to be running locally).

**Browse**
`HighlightsFragment` — accessible from the nav drawer. Shows highlights grouped by podcast/episode. Tap any highlight to re-open `HighlightEditBottomSheet` for edits or re-sync.

**Config classes**
| Class | Stores |
|---|---|
| `WhisperPreferences` | Lookback duration, model path |
| `ReadwisePreferences` | Readwise API token |
| `RoamPreferences` | Roam API port, graph name |

**DB**
`PodcastHighlight` model → `podcast_highlights` table in `PodDBAdapter`. `DBUpgrader` handles migration. Read via `DBReader.getPodcastHighlights()`.

**Transcription engine**
`:feature:sherpa-onnx` is a thin Gradle module that pulls in the `sherpa-onnx-android` AAR. `WhisperTranscriptionService` is the only consumer. Model files (4 × `.onnx`) are downloaded at runtime, not bundled.

---

## AGENTS.md Rules (enforced)

- No comments in new code
- Java only (not Kotlin)
- Minimal diffs — read every file before editing
