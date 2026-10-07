#!/usr/bin/env python3
"""Synthesizes the app's sounds into resources/ (stdlib only, no numpy/ffmpeg).

The APK ships no audio: resources/manifest.json + these files are downloaded from GitHub
on first launch. Re-run after tweaking; publish changed audio under a new file name.
"""
import json, math, os, random, struct, wave

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "resources")
random.seed(1410)


def write(name, samples):
    peak = max(1e-9, max(abs(x) for x in samples))
    k = 0.9 / peak
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(x * k * 32767)) for x in samples))


# Tibetan singing bowl: inharmonic partials, each split in two for the characteristic beating.
BOWL = [(1.0, 1.0, 6.0), (2.76, 0.55, 4.0), (5.40, 0.30, 2.5), (8.93, 0.15, 1.5)]


def strike(buf, at, f0, gain=1.0, decay=1.0, wrap=False):
    n = len(buf)
    start = int(at * SR)
    length = int(max(d for _, _, d in BOWL) * decay * 5 * SR)
    for ratio, amp, tau in BOWL:
        f = f0 * ratio
        t_decay = tau * decay
        for j in range(length):
            i = start + j
            if i >= n:
                if not wrap:
                    break
                i %= n
            t = j / SR
            env = amp * gain * math.exp(-t / t_decay) * min(1.0, t / 0.004)
            buf[i] += env * (math.sin(2 * math.pi * f * t) + 0.6 * math.sin(2 * math.pi * f * 1.004 * t + 1.3))
    # mallet transient
    for j in range(int(0.015 * SR)):
        if start + j < n:
            buf[start + j] += gain * 0.25 * random.uniform(-1, 1) * (1 - j / (0.015 * SR))


def tone(seconds, hits, f0, gain=1.0, decay=1.0):
    buf = [0.0] * int(seconds * SR)
    for at in hits:
        strike(buf, at, f0, gain, decay)
    return buf


def seamless(make, seconds, fade=1.5):
    """Generate a bit more than needed and crossfade the tail into the head so it loops cleanly."""
    n, f = int(seconds * SR), int(fade * SR)
    x = make(n + f)
    for i in range(f):
        a = i / f
        x[i] = x[i] * a + x[n + i] * (1 - a)
    return x[:n]


def brown(n, leak=0.02):
    out, y = [], 0.0
    for _ in range(n):
        y = (1 - leak) * y + random.uniform(-1, 1) * 0.1
        out.append(y)
    return out


def rain(n):
    out, lp = [], 0.0
    for _ in range(n):
        w = random.uniform(-1, 1)
        lp += 0.25 * (w - lp)
        out.append(0.35 * (w - lp) + 0.25 * lp)  # hiss + softer body
    for _ in range(n // 400):  # droplets
        i, f = random.randrange(n), random.uniform(1500, 4000)
        a = random.uniform(0.1, 0.5)
        for j in range(int(0.02 * SR)):
            if i + j < n:
                out[i + j] += a * math.exp(-j / (0.003 * SR)) * math.sin(2 * math.pi * f * j / SR)
    return out


def ocean(n):
    b = brown(n, 0.01)
    period = 8 * SR  # 5 waves per 40 s loop
    return [x * (0.15 + 0.85 * (0.5 - 0.5 * math.cos(2 * math.pi * i / period)) ** 2) for i, x in enumerate(b)]


def bowl_drone(seconds=40.0):
    buf = [0.0] * int(seconds * SR)
    for k, at in enumerate(range(0, int(seconds), 10)):
        strike(buf, at, [196, 247, 220, 165][k % 4], gain=0.6, decay=1.6, wrap=True)
    return buf


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write("gong.wav", tone(7, [0], 196))
    write("gong_double.wav", tone(8, [0, 1.6], 220))
    write("gong_long.wav", tone(12, [0, 2.5, 5.0], 165, decay=1.4))
    write("bell.wav", tone(4, [0], 523, gain=0.5, decay=0.6))
    write("brown_noise.wav", seamless(brown, 30))
    write("rain.wav", seamless(rain, 30))
    write("ocean.wav", seamless(ocean, 40))
    write("bowl.wav", bowl_drone())
    manifest = {
        "sounds": [
            {"id": "gong", "name": "Gong", "file": "gong.wav"},
            {"id": "gong_double", "name": "Double gong", "file": "gong_double.wav"},
            {"id": "gong_long", "name": "Long gong", "file": "gong_long.wav"},
            {"id": "bell", "name": "Soft bell", "file": "bell.wav"},
            {"id": "brown_noise", "name": "Brown noise", "file": "brown_noise.wav", "loop": True},
            {"id": "rain", "name": "Rain", "file": "rain.wav", "loop": True},
            {"id": "ocean", "name": "Ocean", "file": "ocean.wav", "loop": True},
            {"id": "bowl", "name": "Singing bowls", "file": "bowl.wav", "loop": True},
        ]
    }
    with open(os.path.join(OUT, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
