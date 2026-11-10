"""Hängt Klänge zu einer Vorschau zusammen. Ein "+" vor dem Dateinamen = kurze Pause davor."""
import sys
import numpy as np
import soundfile as sf

SR = 44100
out, files = sys.argv[1], sys.argv[2:]
parts = []
for f in files:
    gap = 0.25 if f.startswith("+") else 1.0
    s, sr = sf.read(f.lstrip("+"))
    if sr != SR:
        s = np.interp(np.arange(int(len(s) * SR / sr)) * sr / SR, np.arange(len(s)), s)
    if parts:
        parts.append(np.zeros(int(SR * gap)))
    parts.append(s)
sf.write(out, np.concatenate(parts), SR)
