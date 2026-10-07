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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Trailer audio for 1.6.6, mixed live. 1.3.x played ONE baked mix; 1.6 waits for a swipe and then switches song on the
 * beat, so the sound ships as six stems (tools/bake/stems.html renders them with the web prototype's own Web Audio
 * graph) and this class does what the web's AudioContext did after them:
 *
 *   bed     Future Pop: intro (with the ducking) then bars 5–8 looping. A lowpass steps it back while the feed
 *           waits (20 kHz → 900 Hz over b17½ → b19½); the drag opens it again ([open]).
 *   sfx     every accent before the swipe, locked to the bed.
 *   teaser  card 2's song (Pop Upbeat), from its drop, on the beat the swipe lands on ([commit]); the bed fades out
 *           into that beat. It dips into the phone (lowpass + gain) under the riser and opens on the G04 downbeat,
 *           then loops bars 5–8 like the bed did.
 *   tail    the accents after the swipe (name pluck, riser, boom, G04 pops), on the same landing beat.
 *   master  a limiter shaped like Web Audio's DynamicsCompressor (−10 dB, 4:1, 6 dB knee, 3 / 150 ms, its automatic
 *           makeup gain included), so the levels match the web.
 *
 * Clock: one AudioTrack; stream frame 0 = timeline time [t0]. Its presentation timestamp gives the time the ear hears
 * now ([timelineTime]); [A7Player] slaves the picture to it. After the swipe the timeline is behind the music by however
 * long the user waited: [shift] maps one onto the other.
 *
 * Policy (user ruling): no mute toggle in onboarding — music simply stays off when the phone is on silent / vibrate or
 * another app is already playing music.
 */
class A7Mixer(private val ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val pcm = HashMap<String, Future<ShortArray?>>()

    /** Decode every stem in the background while the splash plays: what plays first is decoded first. */
    fun prepare() {
        if (pcm.isNotEmpty()) return
        for ((name, frames) in STEMS) pcm[name] = io.submit<ShortArray?> {
            runCatching { decode("a7/audio/$name.ogg", frames) }.onFailure { Log.w(TAG, "decode of $name failed, music stays off", it) }.getOrNull()
        }
    }

    private fun stem(name: String, waitMs: Long): ShortArray? = runCatching { pcm[name]?.get(waitMs, TimeUnit.MILLISECONDS) }.getOrNull()

    /**
     * Ogg Opus asset → interleaved 16-bit stereo PCM of exactly [frames] frames. The decoder drops Opus's pre-skip;
     * the end padding of the last packet is cut here, so the drop, the swipe beat and the loops stay sample-accurate.
     */
    private fun decode(path: String, frames: Int): ShortArray {
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
        try {
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
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
                    check(f.getInteger(MediaFormat.KEY_SAMPLE_RATE) == SR && f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2) { "unexpected output $f" }
                } else if (o >= 0) {
                    val sb = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
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
        Log.i(TAG, "$path: ${n / 2} frames, ${SystemClock.elapsedRealtime() - t} ms")
        return out
    }

    fun allowed(): Boolean = am.ringerMode == AudioManager.RINGER_MODE_NORMAL && !am.isMusicActive

    /** One playback: AudioTrack + the mix thread that feeds it until [kill]. */
    private inner class Stream(val track: AudioTrack, val t0: Double, val bedIntro: ShortArray, val bedLoop: ShortArray, val sfx: ShortArray) {
        @Volatile var alive = true
        var hasStamp = false
        val startedAt = SystemClock.elapsedRealtime()
        @Volatile var written = 0L // frames handed to the AudioTrack so far

        // --- schedule (frames of this stream). Set before start, or by commit() on the UI thread. ---
        val bedAt = ((A7Times.T_DROP - PREROLL - t0) * SR).roundToLong()
        private val muffleFrom = ((b(17.5) - t0) * SR).roundToLong()
        private val muffleTo = ((b(A7Times.N_WAIT) - t0) * SR).roundToLong()
        @Volatile var openTarget = MUFFLED // drag → bed cutoff (smoothed)
        @Volatile var land: Landing? = null
        var teaserIntro: ShortArray? = null
        var teaserLoop: ShortArray? = null
        var tail: ShortArray? = null

        private val bedLp = Biquad(); private val teaserLp = Biquad()
        private var dragCut = MUFFLED
        private val lim = Limiter()

        fun kill() { alive = false; runCatching { track.pause(); track.flush(); track.release() } }

        private fun stereo(src: ShortArray, i: Long, out: FloatArray, gain: Float, add: Boolean) {
            val k = (i * 2).toInt()
            val l = src[k] / 32768f * gain; val r = src[k + 1] / 32768f * gain
            if (add) { out[0] += l; out[1] += r } else { out[0] = l; out[1] = r }
        }

        /** Bed frame at bed position [p] (intro, then the loop forever). */
        private fun bedSample(p: Long, out: FloatArray) {
            if (p < bedIntro.size / 2) stereo(bedIntro, p, out, 1f, false)
            else stereo(bedLoop, (p - bedIntro.size / 2) % (bedLoop.size / 2), out, 1f, false)
        }

        fun mix(block: ShortArray, frames: Int) {
            val f0 = written
            val l = land
            // cutoffs and gains are set once per block (5 ms): fine for sweeps of a beat or more
            val mid = f0 + frames / 2
            dragCut += (openTarget - dragCut) * (1 - exp(-frames.toDouble() / SR / 0.04)).toFloat()
            val bedCut = when {
                mid < muffleFrom -> OPEN
                mid < muffleTo -> expRamp(OPEN, MUFFLED, (mid - muffleFrom).toDouble() / (muffleTo - muffleFrom))
                else -> dragCut
            }
            bedLp.lowpass(bedCut, 0.9f)
            var teaserCut = OPEN; var tFrom = 0.6f; var tTo = 0.6f
            if (l != null) {
                val fly = l.at + (3 * BEAT * SR).roundToLong()
                val g4 = l.at + (4 * BEAT * SR).roundToLong()
                val a = g4 - (0.03 * SR).roundToLong(); val z = g4 + (0.02 * SR).roundToLong()
                teaserCut = when {
                    mid < fly -> OPEN
                    mid < a -> expRamp(OPEN, 500f, (mid - fly).toDouble() / (a - fly))
                    mid < z -> expRamp(500f, OPEN, (mid - a).toDouble() / (z - a))
                    else -> OPEN
                }
                fun tg(f: Long) = when {
                    f < fly -> 0.6f
                    f < a -> 0.6f + (0.35f - 0.6f) * ((f - fly).toFloat() / (a - fly))
                    f < z -> 0.35f + (0.55f - 0.35f) * ((f - a).toFloat() / (z - a))
                    else -> 0.55f
                }
                tFrom = tg(f0); tTo = tg(f0 + frames)
            }
            teaserLp.lowpass(teaserCut, 0.7f)
            val s = FloatArray(2); val acc = FloatArray(2)
            for (i in 0 until frames) {
                val f = f0 + i
                acc[0] = 0f; acc[1] = 0f
                // bed (+ its accents)
                val p = f - bedAt
                if (p >= 0) {
                    var bg = 1f
                    if (l != null) bg = when {
                        f >= l.at -> 0f
                        else -> (1f - (f - l.fadeFrom).toFloat() / (l.at - l.fadeFrom)).coerceIn(0f, 1f)
                    }
                    if (bg > 0f) {
                        bedSample(p, s)
                        bedLp.run(s)
                        acc[0] += s[0] * bg; acc[1] += s[1] * bg
                    }
                    if (p < sfx.size / 2) { stereo(sfx, p, s, SFX_GAIN, false); acc[0] += s[0]; acc[1] += s[1] }
                }
                if (l != null && f >= l.at) {
                    val q = f - l.at
                    val ti = teaserIntro; val tl = teaserLoop
                    if (ti != null && tl != null) {
                        if (q < ti.size / 2) stereo(ti, q, s, 1f, false) else stereo(tl, (q - ti.size / 2) % (tl.size / 2), s, 1f, false)
                        teaserLp.run(s)
                        val g = tFrom + (tTo - tFrom) * i / frames
                        acc[0] += s[0] * g; acc[1] += s[1] * g
                    }
                    val tt = tail
                    if (tt != null && q < tt.size / 2) { stereo(tt, q, s, SFX_GAIN, false); acc[0] += s[0]; acc[1] += s[1] }
                }
                lim.run(acc)
                block[i * 2] = (acc[0].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                block[i * 2 + 1] = (acc[1].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            written = f0 + frames
        }
    }

    /** Where the teaser starts: stream frame [at]; the bed fades from [fadeFrom] to 0 at [at]. */
    class Landing(val at: Long, val fadeFrom: Long)

    private var cur: Stream? = null
    private val ts = AudioTimestamp()
    private var focus: AudioFocusRequest? = null

    /** Timeline − music: how far the swipe pushed the music ahead of the timeline (0 before the swipe). */
    @Volatile var shift = 0.0; private set

    /** Output started but not yet reporting a timestamp (≤ 0.5 s) — the picture should hold still. */
    val pending get() = cur?.let { !it.hasStamp && SystemClock.elapsedRealtime() - it.startedAt < 500 } ?: false

    /** Is music playing (so the wait and the swipe follow its clock)? */
    val playing get() = cur != null

    /** Start the stream so that frame 0 plays at timeline time [tl0] (the ad pause point). */
    fun start(tl0: Double) {
        stop()
        shift = 0.0
        if (!allowed()) return
        // Decoding normally ends during the splash; never hold the UI thread more than 1 s for it.
        val bi = stem("bed_intro", 1000) ?: return
        val bl = stem("bed_loop", 1000) ?: return
        val sx = stem("sfx_intro", 1000) ?: return

        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val fr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { if (it < 0) main.post { stop() } }
            .build()
        focus?.let(am::abandonAudioFocusRequest)
        if (am.requestAudioFocus(fr) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = fr

        val fmt = AudioFormat.Builder().setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs).setAudioFormat(fmt)
            .setBufferSizeInBytes(maxOf(min * 2, SR * 4 / 10)) // ~100 ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val st = Stream(track, tl0, bi, bl, sx)
        cur = st
        track.play()
        Thread {
            val block = ShortArray(BLOCK * 2)
            try {
                while (st.alive) {
                    st.mix(block, BLOCK)
                    var off = 0
                    while (off < block.size && st.alive) {
                        val n = st.track.write(block, off, block.size - off)
                        if (n < 0) return@Thread
                        off += n
                    }
                }
            } catch (_: IllegalStateException) { /* released */ }
        }.apply { name = "a7-mixer"; priority = Thread.MAX_PRIORITY; start() }
    }

    /** Music time the listener hears at the moment this frame reaches the screen (before [shift]), or null if no music. */
    fun musicTime(frameTimeNanos: Long): Double? {
        val st = cur ?: return null
        if (!st.track.getTimestamp(ts)) return null
        st.hasStamp = true
        // The frame being built now is shown ~1 vsync later; ask the audio clock about that moment.
        val showAt = frameTimeNanos + DISPLAY_LATENCY_NS
        val frames = ts.framePosition + (showAt - ts.nanoTime) * SR / 1e9
        return st.t0 + frames / SR
    }

    /** The drag opens the bed: [p] 0 = at rest (muffled), 1 = at the threshold (open). */
    fun open(p: Float) { cur?.openTarget = MUFFLED * (OPEN / MUFFLED).pow(p.coerceIn(0f, 1f)) }

    /**
     * The swipe: card 2 lands on the music's next beat at least 0.3 s away. The bed fades out into that beat, the
     * teaser and its accents start on it. Returns that beat's music time, or null without music.
     */
    fun commit(nowMusic: Double): Double? {
        val st = cur ?: return null
        // the teaser is only needed from here on; it was decoded long ago
        val ti = stem("teaser_intro", 300); val tl = stem("teaser_loop", 300); val tt = stem("sfx_tail", 300)
        var n = ceil((nowMusic + 0.3 - A7Times.T_DROP) / BEAT)
        var at = ((b(n) - st.t0) * SR).roundToLong()
        val safe = st.written + BLOCK * 2
        while (at < safe) { n += 1; at = ((b(n) - st.t0) * SR).roundToLong() }
        st.teaserIntro = ti; st.teaserLoop = tl; st.tail = tt
        st.land = Landing(at, st.written + BLOCK)
        shift = b(n) - b(A7Times.N_PLAY)
        return b(n)
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

    /** RBJ low-pass, stereo, coefficients per block. */
    private class Biquad {
        private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
        private val x1 = FloatArray(2); private val x2 = FloatArray(2); private val y1 = FloatArray(2); private val y2 = FloatArray(2)
        private var last = -1f
        fun lowpass(f: Float, q: Float) {
            if (f == last) return
            last = f
            val fc = min(f, SR * 0.49f)
            val w = 2 * PI * fc / SR
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            val a0 = 1 + alpha
            b0 = ((1 - c) / 2 / a0).toFloat(); b1 = ((1 - c) / a0).toFloat(); b2 = b0
            a1 = (-2 * c / a0).toFloat(); a2 = ((1 - alpha) / a0).toFloat()
        }
        fun run(s: FloatArray) {
            for (ch in 0..1) {
                val x = s[ch]
                val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                x2[ch] = x1[ch]; x1[ch] = x; y2[ch] = y1[ch]; y1[ch] = y
                s[ch] = y
            }
        }
    }

    /**
     * Web Audio DynamicsCompressor, simplified: peak detector (attack 3 ms, release 150 ms), threshold −10 dB, 4:1
     * over a 6 dB soft knee, then the automatic makeup gain the spec applies ((1 / gain at 0 dBFS)^0.6 ≈ +4.5 dB).
     */
    private class Limiter {
        private var env = 0f
        private val att = exp(-1.0 / (0.003 * SR)).toFloat()
        private val rel = exp(-1.0 / (0.15 * SR)).toFloat()
        private val makeup = 10f.pow(-curve(0f) / 20f).pow(0.6f)
        fun run(s: FloatArray) {
            val x = max(abs(s[0]), abs(s[1]))
            env = if (x > env) att * env + (1 - att) * x else rel * env + (1 - rel) * x
            val inDb = if (env > 1e-6f) 20f * log10(env) else -120f
            val g = 10f.pow((curve(inDb) - inDb) / 20f) * makeup
            s[0] *= g; s[1] *= g
        }
        companion object {
            private const val T = -10f; private const val R = 4f; private const val K = 6f
            /** Output level (dB) for an input level (dB). */
            fun curve(x: Float): Float = when {
                x < T - K / 2 -> x
                x > T + K / 2 -> T + (x - T) / R
                else -> x + (1 / R - 1) * (x - T + K / 2).pow(2) / (2 * K)
            }
        }
    }

    companion object {
        private const val TAG = "A7Mixer"
        private const val DISPLAY_LATENCY_NS = 16_666_667L
        const val SR = 48000
        private const val BLOCK = 240 // 5 ms
        /** Both songs: 120 BPM stretched to 5/6 (100 BPM), files start 0.36 s before their drop. */
        const val BPM = 100
        const val BEAT = 60.0 / BPM
        const val PREROLL = 0.36
        private const val SFX_GAIN = 2f // accent stems are stored at ×0.5 (tools/bake/stems.html)
        private const val MUFFLED = 900f // Hz — the bed steps back while the feed waits
        private const val OPEN = 20000f
        private fun b(n: Double) = A7Times.T_DROP + n * BEAT
        private fun expRamp(a: Float, z: Float, t: Double) = (a * (z / a).toDouble().pow(t.coerceIn(0.0, 1.0))).toFloat()

        /** Stem → exact length in 48 kHz frames (tools/encode_stems.sh prints them). Decode order = play order. */
        val STEMS = linkedMapOf(
            "bed_intro" to 938_880, "sfx_intro" to 578_880, "bed_loop" to 460_800,
            "teaser_intro" to 921_600, "sfx_tail" to 177_600, "teaser_loop" to 460_800,
        )
    }
}
