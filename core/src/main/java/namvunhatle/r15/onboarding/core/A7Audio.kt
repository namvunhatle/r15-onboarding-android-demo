package namvunhatle.r15.onboarding.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.exp

/**
 * Trailer audio. The web prototype synthesised its accents (pluck / whoosh / pop / ring / tick) and ducking live
 * with Web Audio; here the SAME graph was rendered offline into two files per track (tools/bake):
 *   `<id>_intro.wav` = file 0 → beat 32 with every accent + ducking · `<id>_loop.wav` = beats 16–32, bed only (G04 loop).
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
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var track: Track? = null
    private var pcm: Future<Pair<ShortArray, ShortArray>?>? = null

    /** One playback: an AudioTrack + the thread that keeps it fed (silence → intro → loop forever) until [kill]. */
    private class Stream(val track: AudioTrack, val t0: Double) {
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
        pcm = track?.let { tr -> io.submit<Pair<ShortArray, ShortArray>?> { readWav("audio/${tr.id}_intro.wav") to readWav("audio/${tr.id}_loop.wav") } }
    }

    private fun readWav(path: String): ShortArray {
        val bytes = ctx.assets.open(path).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(bb.remaining()).also { bb.get(it) }
    }

    fun allowed(): Boolean = am.ringerMode == AudioManager.RINGER_MODE_NORMAL && !am.isMusicActive

    /** Start the stream so that frame 0 plays at timeline time [tl0] (the ad pause point). */
    fun start(tl0: Double) {
        stop()
        val tr = track ?: return
        if (!allowed()) return
        val data = runCatching { pcm?.get() }.getOrNull() ?: return
        val (intro, loop) = data

        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val fr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { if (it < 0) main.post { stop() } }
            .build()
        focus?.let(am::abandonAudioFocusRequest)
        if (am.requestAudioFocus(fr) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = fr

        val sr = SR
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
        val st = Stream(track, tl0)
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
        val frames = ts.framePosition + (showAt - ts.nanoTime) * SR / 1e9
        return st.t0 + frames / SR
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
        const val SR = 44100
        private const val DISPLAY_LATENCY_NS = 16_666_667L
    }
}
