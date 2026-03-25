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

## AGENTS.md Rules (enforced)

- No comments in new code
- Java only (not Kotlin)
- Minimal diffs — read every file before editing
