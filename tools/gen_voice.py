"""Erzeugt die eingebauten englischen Ansagen mit dem Kokoro-Stimmenmix "John".

Sehr kurze Sätze klingen bei Kokoro einzeln erzeugt schwächer. Darum werden
"charged" und "low" in einem Satz erzeugt und an der Pause dazwischen geschnitten.

Aufruf: <kokoro-venv>/bin/python gen_voice.py <ausgabeordner>
"""
import json
import os
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
import kokoro_onnx

# Folder of the Kokoro-82M model; set KOKORO_DIR if it lives elsewhere.
KOKORO = Path(os.environ.get("KOKORO_DIR", "Kokoro-82M"))
MIX = KOKORO / "py_hub/configs/Voice_John_Wheeler.json"
SPEED = 0.9

# Text -> Namen der Teile (getrennt an der längsten Pause in der Mitte)
PAIRS = {
    "Battery charged; Battery low.": ("battery_charged", "battery_low"),
    "Battery charged. Battery low.": ("battery_charged_punkt", "battery_low_punkt"),
}
SINGLES = {
    "Battery hot.": "battery_hot",
    "Battery cold.": "battery_cold",
}

FRAME = 0.01


def frame_rms(s, sr):
    n = int(sr * FRAME)
    return np.array([np.sqrt(np.mean(s[i:i + n] ** 2)) for i in range(0, len(s) - n, n)])


def trim(s, sr, rel=0.02, pad=0.05, fade=0.015):
    r = frame_rms(s, sr)
    loud = np.where(r > rel * r.max())[0]
    n = int(sr * FRAME)
    a = max(int(loud[0] * n - pad * sr), 0)
    b = min(int((loud[-1] + 1) * n + pad * sr), len(s))
    s = s[a:b].copy()
    f = int(sr * fade)
    s[:f] *= np.linspace(0, 1, f)
    s[-f:] *= np.linspace(1, 0, f)
    return s / np.max(np.abs(s)) * 0.89


def split_at_pause(s, sr, rel=0.02):
    r = frame_rms(s, sr)
    quiet = r < rel * r.max()
    runs, start = [], None
    for i, q in enumerate(quiet):
        if q and start is None:
            start = i
        if not q and start is not None:
            runs.append((start, i))
            start = None
    lo, hi = 0.3 * len(r), 0.7 * len(r)
    a, b = max((x for x in runs if lo < x[0] < hi), key=lambda x: x[1] - x[0])
    cut = int((a + b) / 2 * sr * FRAME)
    return s[:cut], s[cut:]


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    cfg = json.loads(MIX.read_text())
    weights = {v: w for v, w in cfg["voice_weights"].items() if w > 0}
    total = sum(weights.values())
    tts = kokoro_onnx.Kokoro(str(KOKORO / "kokoro.onnx"), str(KOKORO / "voices-v1.0.bin"))
    voice = sum(tts.voices[v] * (w / total) for v, w in weights.items())

    def say(text):
        return tts.create(text, voice=voice, speed=SPEED, lang="en-us")

    for text, names in PAIRS.items():
        s, sr = say(text)
        for name, part in zip(names, split_at_pause(s, sr)):
            part = trim(part, sr)
            sf.write(out / f"{name}.wav", part, sr)
            print(f"{name}.wav {len(part) / sr:.2f}s")
    for text, name in SINGLES.items():
        s, sr = say(text)
        s = trim(s, sr)
        sf.write(out / f"{name}.wav", s, sr)
        print(f"{name}.wav {len(s) / sr:.2f}s")


if __name__ == "__main__":
    main()
