# Interval Timer

A low-power workday timer for Android (built for the Pixel 10 / Android 16): start it once in the
morning, put the phone away, and it runs **8 × (50 min work + 10 min break)** on its own.

- **Gong + vibration** at every transition (single gong → break, double → work, long → day done)
- **Breaks with a 2-min prepare phase**, then an activity: box breathing, meditation, NSDR,
  power nap, stretch, walk, eye reset, hydrate, ambient sound, silence
- **Long breaks** every N cycles (e.g. a 20-min power nap)
- **Focus music** during work (brown noise, rain, ocean, singing bowls), screen off
- **Actionable notification** with countdown, Pause and Skip — no need to open the app
- **Restart-proof**: phases are absolute timestamps, rebuilt after reboot
- **Local-first**: settings and session go to Android's Google backup; no account, no analytics
- **Auto-update** from GitHub Releases

## How it stays light

No background ticking. Each phase end is one `AlarmManager` exact alarm; the notification shows
a system-rendered countdown. A foreground service exists only while audio is playing.

## Resources

Audio is **not bundled in the APK**. [`resources/`](resources) holds the sounds and
`manifest.json`; the app downloads them from this repo on first launch and caches them offline.
Sounds are synthesized by [`tools/gen_sounds.py`](tools/gen_sounds.py). To add a sound, drop a file
in `resources/` and add an entry to `manifest.json` (`"loop": true` makes it selectable as music).

## Build & release

```bash
./gradlew testDebugUnitTest assembleDebug
scripts/release.sh 1.0.1 "What changed"
```

`release.sh` bumps `VERSION_NAME`, builds a signed APK, tags, pushes and creates the GitHub
release. The installed app checks `releases/latest` on launch, downloads a newer APK and offers
**Install**. Signing reads `IT_STORE_FILE`, `IT_STORE_PASSWORD`, `IT_KEY_ALIAS`, `IT_KEY_PASSWORD`
from `~/.gradle/gradle.properties`; the keystore stays out of git — **back it up**, every update
must be signed with the same key.
