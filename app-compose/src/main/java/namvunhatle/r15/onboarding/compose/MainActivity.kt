package namvunhatle.r15.onboarding.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import namvunhatle.r15.onboarding.core.A7Art
import namvunhatle.r15.onboarding.core.A7Native
import namvunhatle.r15.onboarding.core.A7Player
import namvunhatle.r15.onboarding.core.Scene
import namvunhatle.r15.onboarding.core.immersive
import namvunhatle.r15.onboarding.core.dumpExtra
import namvunhatle.r15.onboarding.core.seekExtra
import namvunhatle.r15.onboarding.core.viewport

/** A7 onboarding — Jetpack Compose build. Same engine, native art and audio as the Views build (module :core). */
class MainActivity : ComponentActivity() {
    private lateinit var player: A7Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        val scene = Scene(this, viewport())
        // Start every bake and decode on the start-up pool first, so they run while the scene composes.
        val art = A7Art(this)
        val native = A7Native(this, scene)
        player = A7Player(this, scene)
        seekExtra()?.let(player::seekFrozen)
        if (dumpExtra()) native.dump(getExternalFilesDir("dump")!!)
        setContent { A7Screen(player, art, native) }
    }

    override fun onStop() {
        super.onStop()
        // Leaving mid-trailer (home, call, lock): the web never had this case. Prototype rule = start over next time.
        // Not while waiting on the ad or on a destination: a real interstitial SDK opens its own Activity, which
        // stops this one — resetting there would throw the user back to the splash after every ad.
        if (!isChangingConfigurations && !player.atAd && player.dest == null) player.replay()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
