package namvunhatle.r15.onboarding.core

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A value made on the start-up pool. Everything the scene bakes or decodes (blurs, glows, native art, screenshots) is
 * submitted in onCreate, splash pieces first, so the work runs while the main thread inflates / composes, and a UI
 * waits on [value] only where it first draws the piece. Most pieces show seconds into the trailer and are done by then.
 *
 * Jobs run in submission order, so a job may wait on a [Later] submitted before it — never on a later one.
 */
class Later<T> private constructor(private val f: CompletableFuture<T>) {
    val value: T get() = f.get()
    val isDone get() = f.isDone

    /** Run [cb] on the main thread once the value is made. */
    fun onDone(cb: () -> Unit) { f.thenRunAsync(cb, main) }

    companion object {
        private val pool = Executors.newFixedThreadPool(3) { r -> Thread(r, "a7-bake").apply { isDaemon = true } }
        private val main = Executor(Handler(Looper.getMainLooper())::post)

        fun <T> of(job: () -> T) = Later(CompletableFuture.supplyAsync({ job() }, pool))
    }
}

/** A drawable made on the start-up pool ([Later]): the first draw waits for it. */
class LaterDrawable(val later: Later<out Drawable>) : Drawable() {
    override fun draw(canvas: Canvas) = later.value.let { it.bounds = bounds; it.draw(canvas) }

    // Opacity is animated on the element's layer, never on the drawable.
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
