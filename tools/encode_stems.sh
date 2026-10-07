#!/bin/sh
# Encode the 1.6.6 stems (tools/bake/stems.mjs output, 48 kHz WAV) into the Ogg Opus files the library ships.
#   tools/encode_stems.sh <wav dir> [assets dir]
# 96 kbps, 120 ms packets (6× fewer decoder round trips than 20 ms, so decoding fits the splash). No resampling:
# the stems are already 48 kHz, so the frame counts printed here are exact — copy them into stems.json; A7Mixer
# trims each decoded stem to that length so the drop, the swipe beat and the loops stay sample-accurate.
set -e
SRC=$1
OUT=${2:-$(cd "$(dirname "$0")/.." && pwd)/a7onboarding/src/main/assets/a7/audio}
mkdir -p "$OUT"
for s in bed_intro bed_loop sfx_intro teaser_intro teaser_loop sfx_tail; do
  ffmpeg -v error -y -i "$SRC/$s.wav" -c:a libopus -b:a 96k -vbr on -application audio -frame_duration 120 "$OUT/$s.ogg"
  frames=$(( ($(wc -c < "$SRC/$s.wav") - 44) / 4 ))
  echo "$s.ogg  $(($(wc -c < "$OUT/$s.ogg") / 1024)) KB  frames=$frames"
done
