package namvunhatle.r15.onboarding.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.exp

/**
 * Trailer audio. The web prototype synthesised its accents (pluck / whoosh / pop / ring / tick) and ducking live
 * with Web Audio; here the SAME graph was rendered offline into two files per track (tools/bake):
 *   `<id>_intro` = file 0 → beat 32 with every accent + ducking · `<id>_loop` = beats 16–32, bed only (G04 loop).
 * They ship as Ogg Opus (tools/encode_audio.sh, 48 kHz, 96 kbps) and are decoded to PCM once, during the splash.
 * That is also how a shipping app would do it: the sound designer delivers stems, the app only plays them in sync.
 *
 * Sync: one AudioTrack stream = [silence up to the drop's pre-roll] + intro + loop forever. Its presentation
 * timestamp gives the timeline time the ear is hearing now; [A7Player] slaves the picture to it.
 *
 * Policy (user ruling): no mute toggle in onboarding — music simply stays off when the phone is on silent /
 * vibrate or another app is already playing music.
 */
class A7Audio(private val ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val io = Executors.newFixedThreadPool(2) // intro and loop decode side by side
    private val main = Handler(Looper.getMainLooper())

    private var track: Track? = null
    private var pcm: Pair<Future<ShortArray?>, Future<ShortArray?>>? = null

    /** One playback: an AudioTrack + the thread that keeps it fed (silence → intro → loop forever) until [kill]. */
    private class Stream(val track: AudioTrack, val t0: Double, val sr: Int) {
        @Volatile var alive = true
        var hasStamp = false
        val startedAt = SystemClock.elapsedRealtime()
        fun kill() { alive = false; runCatching { track.pause(); track.flush(); track.release() } }
    }

    private var cur: Stream? = null // the stream whose clock drives the picture (null while fading out / off)
    private val ts = AudioTimestamp()
    private var focus: AudioFocusRequest? = null

    /** Output started but not yet reporting a timestamp (≤ 0.5 s) — the picture should hold still. */
    val pending get() = cur?.let { !it.hasStamp && SystemClock.elapsedRealtime() - it.startedAt < 500 } ?: false

    /** Decode the chosen track in the background while the splash plays. */
    fun prepare(track: Track?) {
        this.track = track
        pcm = track?.let { tr ->
            fun job(part: String, frames: Int) = io.submit<ShortArray?> {
                runCatching { decode(tr, "audio/${tr.id}_$part.ogg", frames) }
                    .onFailure { Log.w(TAG, "decode of $part failed, music stays off", it) }.getOrNull()
            }
            job("intro", tr.introFrames) to job("loop", tr.loopFrames)
        }
    }

    /**
     * Ogg Opus asset → interleaved 16-bit stereo PCM of exactly [frames] frames. The decoder drops Opus's
     * pre-skip; the end padding of the last packet is cut here, so the drop and the G04 loop stay sample-accurate.
     */
    private fun decode(tr: Track, path: String, frames: Int): ShortArray {
        val t = SystemClock.elapsedRealtime()
        val bytes = ctx.assets.open(path).use { it.readBytes() }
        val ex = MediaExtractor()
        ex.setDataSource(object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= bytes.size) return -1
                val n = minOf(size, bytes.size - position.toInt())
                System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                return n
            }
            override fun getSize() = bytes.size.toLong()
            override fun close() {}
        })
        ex.selectTrack(0)
        val fmt = ex.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        val out = ShortArray(frames * 2)
        var n = 0
        var decoded = 0L
        try {
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                // Fill every free input slot without waiting; only the output side blocks (briefly).
                while (!inputDone) {
                    val i = codec.dequeueInputBuffer(0)
                    if (i < 0) break
                    val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                }
                val o = codec.dequeueOutputBuffer(info, 2_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    check(f.getInteger(MediaFormat.KEY_SAMPLE_RATE) == tr.sampleRate && f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2) { "unexpected output $f" }
                } else if (o >= 0) {
                    val sb = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    decoded += sb.remaining() / 2
                    val take = minOf(sb.remaining(), out.size - n)
                    sb.get(out, n, take)
                    n += take
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.release()
            ex.release()
        }
        Log.i(TAG, "$path: decoded $decoded frames, kept $frames, ${SystemClock.elapsedRealtime() - t} ms")
        return out
    }

    fun allowed(): Boolean = am.ringerMode == AudioManager.RINGER_MODE_NORMAL && !am.isMusicActive

    /** Start the stream so that frame 0 plays at timeline time [tl0] (the ad pause point). */
    fun start(tl0: Double) {
        stop()
        val tr = track ?: return
        if (!allowed()) return
        // Decoding normally ends during the splash; never hold the UI thread more than 1 s for it.
        val (fi, fl) = pcm ?: return
        val intro = runCatching { fi.get(1, TimeUnit.SECONDS) }.getOrNull() ?: return
        val loop = runCatching { fl.get(1, TimeUnit.SECONDS) }.getOrNull() ?: return

        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val fr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { if (it < 0) main.post { stop() } }
            .build()
        focus?.let(am::abandonAudioFocusRequest)
        if (am.requestAudioFocus(fr) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = fr

        val sr = tr.sampleRate
        val fmt = AudioFormat.Builder().setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs).setAudioFormat(fmt)
            .setBufferSizeInBytes(maxOf(min * 2, sr * 4 / 10)) // ~100 ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        // file position 0 sits at T_DROP − preroll on the timeline
        val origin = A7Times.T_DROP - tr.preroll
        val silence = ShortArray(((origin - tl0) * sr).toInt().coerceAtLeast(0) * 2)
        val st = Stream(track, tl0, sr)
        cur = st
        track.play()
        Thread {
            try {
                if (write(st, silence) && write(st, intro)) while (write(st, loop)) { /* G04 bed loops until stopped */ }
            } catch (_: IllegalStateException) { /* released */ }
        }.apply { name = "a7-audio"; start() }
    }

    private fun write(st: Stream, d: ShortArray): Boolean {
        var off = 0
        while (off < d.size) {
            if (!st.alive) return false
            val n = st.track.write(d, off, minOf(4096, d.size - off))
            if (n < 0) return false
            off += n
        }
        return true
    }

    /** Timeline time the listener hears at the moment this frame reaches the screen, or null if no music. */
    fun timelineTime(frameTimeNanos: Long): Double? {
        val st = cur ?: return null
        if (!st.track.getTimestamp(ts)) return null
        st.hasStamp = true
        // The frame being built now is shown ~1 vsync later; ask the audio clock about that moment.
        val showAt = frameTimeNanos + DISPLAY_LATENCY_NS
        val frames = ts.framePosition + (showAt - ts.nanoTime) * st.sr / 1e9
        return st.t0 + frames / st.sr
    }

    /** Stop, optionally with the web's fade (exponential, τ = fade / 3). The stream keeps playing while it fades. */
    fun stop(fade: Double = 0.0) {
        val st = cur ?: return
        cur = null
        if (fade <= 0) { end(st); return }
        val begin = SystemClock.uptimeMillis()
        main.post(object : Runnable {
            override fun run() {
                val s = (SystemClock.uptimeMillis() - begin) / 1000.0
                if (s >= fade || !st.alive) { end(st); return }
                runCatching { st.track.setVolume(exp(-s / (fade / 3)).toFloat()) }
                main.postDelayed(this, 16)
            }
        })
    }

    private fun end(st: Stream) {
        st.kill()
        if (cur == null) focus?.let { am.abandonAudioFocusRequest(it); focus = null }
    }

    fun release() { stop(); io.shutdown() }

    companion object {
        private const val TAG = "A7Audio"
        private const val DISPLAY_LATENCY_NS = 16_666_667L
    }
}
