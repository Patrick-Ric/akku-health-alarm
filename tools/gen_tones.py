"""Erzeugt die eigenen Alarmtöne (frei von fremden Rechten) als WAV.

Aufruf: python3 gen_tones.py <ausgabeordner>
"""
import sys
import wave
from pathlib import Path

import numpy as np

SR = 44100


def note(freq, dur, partials, attack=0.004):
    t = np.arange(int(SR * dur)) / SR
    s = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t / d) for r, a, d in partials)
    env = np.minimum(t / attack, 1.0)
    return s * env


def sequence(freqs, step, dur, partials, attack=0.004):
    total = int(SR * (step * (len(freqs) - 1) + dur))
    out = np.zeros(total)
    for i, f in enumerate(freqs):
        n = note(f, dur, partials, attack)
        start = int(SR * step * i)
        out[start:start + len(n)] += n
    return out


def save(path, s, peak=0.7):
    s = s / np.max(np.abs(s)) * peak
    fade = int(SR * 0.02)
    s[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((s * 32767).astype(np.int16).tobytes())
    print(path, f"{len(s) / SR:.2f}s")


C5, E5, G5, A5, C6, E6 = 523.25, 659.26, 783.99, 880.0, 1046.5, 1318.5

VARIANTS = {
    # Holz/Marimba: Grundton + schnell abklingender 4. Teilton
    "a_marimba": dict(partials=[(1, 1.0, 0.35), (4, 0.25, 0.04), (10, 0.05, 0.01)],
                      up=[C5, E5, G5], down=[G5, E5, C5], step=0.15, dur=0.9),
    # Glasglocke: unharmonische Teiltöne
    "b_glocke": dict(partials=[(1, 1.0, 0.6), (2.76, 0.35, 0.25), (5.40, 0.12, 0.12), (8.93, 0.05, 0.06)],
                     up=[A5, E6], down=[E6, A5], step=0.22, dur=1.3),
    # Sanft: fast reiner Sinus, weicher Einsatz
    "c_sanft": dict(partials=[(1, 1.0, 0.45), (2, 0.08, 0.2)],
                    up=[E5, A5], down=[A5, E5], step=0.25, dur=1.0, attack=0.03),
}


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for name, v in VARIANTS.items():
        kw = dict(partials=v["partials"], attack=v.get("attack", 0.004))
        save(out / f"{name}_geladen.wav", sequence(v["up"], v["step"], v["dur"], **kw))
        save(out / f"{name}_schwach.wav", sequence(v["down"], v["step"], v["dur"], **kw))
        warn = v["up"][-1]
        save(out / f"{name}_warnung.wav", sequence([warn] * 3, 0.18, 0.5, **kw))


if __name__ == "__main__":
    main()
