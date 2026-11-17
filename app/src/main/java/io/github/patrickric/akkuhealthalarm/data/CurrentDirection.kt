package io.github.patrickric.akkuhealthalarm.data

import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns the raw battery current into mA with a fixed meaning: positive = into the battery, negative = out of it.
 *
 * Android documents "positive = charging", and that is the starting point, so a negative current on the charger
 * shows right away (moto G30: about +170 mA idle, −630 mA under load on a 500 mA USB port).
 * Phones differ, though: some use the opposite sign, some report no sign at all. This is learned per phone:
 * - Unplugged, the battery can only discharge, so the sign seen then is the discharge sign ([dischargeSign]).
 *   Plugged in, a step up of the level shows the battery is charging, so the sign then is the opposite one;
 *   this way a phone that stays on the charger learns it too.
 * - When the learned sign differs from the assumption, the phone counts as reporting a sign ([signed]) only once
 *   a clear current with the other sign shows up on the charger. Until then a plugged-in phone shows the plain
 *   value (counted as charging), so a phone without a sign never shows a normal charge as discharge.
 * With both known, a plugged-in phone that uses more than the charger gives shows a negative current.
 */
class CurrentDirection(dischargeSign: Int = ANDROID_DISCHARGE_SIGN, signed: Boolean = true) {
    /** Sign of the raw value while discharging: -1 (Android's rule, the starting point) or 1. */
    var dischargeSign = dischargeSign
        private set

    /** True once the phone reported both directions. */
    var signed = signed
        private set

    /** Votes for "the raw value is positive while discharging" and against it. */
    private var votesPositive = 0
    private var votesNegative = 0

    /** Level of the previous plugged-in reading, to see a step up; null after unplugging. */
    private var lastPluggedLevel: Int? = null

    /**
     * [rawMa]: value in mA with the phone's own sign; [level] in percent, null if unknown.
     * Returns mA, positive = charging the battery.
     */
    fun apply(rawMa: Int, plugged: Boolean, level: Int? = null): Int {
        val magnitude = abs(rawMa)
        if (!plugged) {
            lastPluggedLevel = null
            if (magnitude >= MIN_MA) vote(rawMa.sign)
            return -magnitude
        }
        val before = lastPluggedLevel
        if (level != null) lastPluggedLevel = level
        // The level just went up on the charger: the current now flows into the battery.
        if (before != null && level != null && level > before && magnitude >= MIN_MA) vote(-rawMa.sign)
        val s = rawMa.sign
        if (magnitude >= MIN_MA && s != 0 && s != dischargeSign) signed = true
        return if (signed && s == dischargeSign) -magnitude else magnitude
    }

    private fun vote(dischargeSignSeen: Int) {
        if (dischargeSignSeen > 0) votesPositive++ else votesNegative++
        if (votesPositive + votesNegative < MIN_VOTES) return
        val learned = if (votesPositive > votesNegative) 1 else -1
        if (learned != dischargeSign) {
            dischargeSign = learned
            signed = false
        }
    }

    companion object {
        /** Android: negative while discharging. */
        const val ANDROID_DISCHARGE_SIGN = -1

        /** Smaller values are noise and say nothing about the direction. */
        const val MIN_MA = 20

        /** Readings (unplugged, or level steps up on the charger) needed before the discharge sign counts as known. */
        const val MIN_VOTES = 3

        /** Plugged in, a current at least this far below zero means the battery is draining despite the charger. */
        const val DRAIN_MA = 20
    }
}
