#!/bin/sh
# Encode baked WAV mixes (tools/bake output) into the Ogg Opus files the app ships.
#   tools/encode_audio.sh <dir with <id>_intro.wav and <id>_loop.wav> <id>
# Writes app-views/src/main/assets/audio/<id>_{intro,loop}.ogg: 48 kHz stereo, 96 kbps, 120 ms packets
# (the longest Opus allows: 6× fewer decoder round trips than the default 20 ms, so decoding fits the splash).
# Opus always decodes at 48 kHz, so the WAVs are resampled here once, not on the phone.
# Prints each file's length in 48 kHz frames: copy them into tracks.json (introFrames / loopFrames);
# A7Audio trims the decoded PCM to exactly that length so the drop and the G04 loop stay sample-accurate.
set -e
SRC=$1; ID=$2
[ -n "$SRC" ] && [ -n "$ID" ] || { echo "usage: $0 <wav dir> <track id>"; exit 1; }
OUT=$(cd "$(dirname "$0")/.." && pwd)/app-views/src/main/assets/audio
mkdir -p "$OUT"
for part in intro loop; do
  ffmpeg -v error -y -i "$SRC/${ID}_$part.wav" \
    -af aresample=48000:filter_size=64:phase_shift=10 \
    -c:a libopus -b:a 96k -vbr on -application audio -frame_duration 120 "$OUT/${ID}_$part.ogg"
  frames=$(ffmpeg -v error -i "$SRC/${ID}_$part.wav" -af aresample=48000:filter_size=64:phase_shift=10 -f s16le -ac 2 - | wc -c)
  echo "${ID}_$part.ogg  $(($(wc -c < "$OUT/${ID}_$part.ogg") / 1024)) KB  ${part}Frames=$((frames / 4))"
done
