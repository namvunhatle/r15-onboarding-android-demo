package namvunhatle.r15.onboarding.core

import android.app.Activity

/**
 * The UA campaign the user installed from decides G04's single primary CTA (Figma: R15 · Christmas, G04 · UA AI
 * Ringtones / G04 · UA Ringtones). There is no secondary button any more; the 48 dp it used stays empty so the native
 * ad below keeps its position and its distance from the CTA.
 *
 * - [AI_RINGTONES]: "Explore AI Ringtones" → Paywall → AI Ringtones.
 * - [RINGTONES]: "Explore Ringtones" → Home (no Paywall, like the old "Browse ringtones"; to be confirmed), with the
 *   one-time magic moment while the Christmas skin is on.
 *
 * Production reads the campaign from attribution data on first launch; when it is unknown (organic, error, timeout)
 * the flow is [AI_RINGTONES] (PROPOSED). This demo takes it from the launch intent: `--es campaign ringtones`.
 */
enum class UaCampaign(val ctaLabel: String, val dest: Dest) {
    AI_RINGTONES("Explore AI Ringtones", Dest.PAYWALL),
    RINGTONES("Explore Ringtones", Dest.HOME);

    companion object {
        /** Set once by the Activity before the scene art is made. */
        @JvmField var current = AI_RINGTONES

        fun parse(v: String?): UaCampaign = when (v?.trim()?.lowercase()) {
            "ringtones", "ringtone", "rt" -> RINGTONES
            else -> AI_RINGTONES
        }
    }
}

/** `adb shell am start … --es campaign ringtones` → [UaCampaign.RINGTONES]; anything else → [UaCampaign.AI_RINGTONES]. */
fun Activity.campaignExtra(): UaCampaign = UaCampaign.parse(intent?.getStringExtra("campaign"))
