#!/bin/sh
# Wandelt die erzeugten WAV-Klänge in Ogg Vorbis für die App um (läuft auch auf Android 7).
# Vorher: gen_voice.py klang-entwurf/stimme  und  gen_tones.py klang-entwurf/toene
set -e
cd "$(dirname "$0")/.."
SRC=klang-entwurf
RAW=app/src/main/res/raw
mkdir -p "$RAW"
conv() { ffmpeg -loglevel error -y -i "$1" -ac 1 -ar 44100 -c:a libvorbis -q:a 4 -map_metadata -1 "$RAW/$2.ogg"; }
conv $SRC/stimme/battery_charged.wav voice_charged
conv $SRC/stimme/battery_low.wav voice_low
conv $SRC/stimme/battery_hot.wav voice_hot
conv $SRC/stimme/battery_cold.wav voice_cold
conv $SRC/toene/a_marimba_geladen.wav tone_charged
conv $SRC/toene/a_marimba_schwach.wav tone_low
conv $SRC/toene/a_marimba_warnung.wav tone_warning
ls -l "$RAW"
