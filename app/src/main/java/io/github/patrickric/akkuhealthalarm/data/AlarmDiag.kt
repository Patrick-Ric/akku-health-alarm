package io.github.patrickric.akkuhealthalarm.data

/**
 * What happened when an alarm sound was played. Stored with the alarm, so that an alarm the user
 * did not hear can be explained afterwards: screen state, volume, audio focus, and how each clip ended.
 */
data class AlarmDiag(
    val screenOn: Boolean,
    val volume: Int,
    val volumeMax: Int,
    /** true = alarm stream ("Also when muted"), false = notification stream. */
    val alarmStream: Boolean,
    val focusGranted: Boolean,
    val doNotDisturb: Boolean,
    val clips: List<Clip>,
) {
    enum class End { OK, ERROR, EXCEPTION, TIMEOUT, STOPPED }

    /** One tone or announcement. [lengthMs] is 0 when unknown (phone voice); [code] = player error. */
    data class Clip(val end: End, val playedMs: Long, val lengthMs: Int, val code: String = "")

    /** First clip that did not play to its end, or null when all did (a little shorter is normal). */
    val problem: Clip?
        get() = clips.firstOrNull { it.end != End.OK || (it.lengthMs > 0 && it.playedMs < it.lengthMs * 0.8) }

    /** Nothing or not everything was played, for a reason other than a newer alarm taking over. */
    val failed: Boolean
        get() = clips.isEmpty() || problem.let { it != null && it.end != End.STOPPED }

    val playedMs: Long get() = clips.sumOf { it.playedMs }

    fun encode(): String = listOf(
        "s=${bit(screenOn)}",
        "v=$volume/$volumeMax",
        "a=${bit(alarmStream)}",
        "f=${bit(focusGranted)}",
        "d=${bit(doNotDisturb)}",
        "c=" + clips.joinToString(",") { "${it.end}:${it.playedMs}:${it.lengthMs}:${it.code}" },
    ).joinToString(";")

    companion object {
        private fun bit(b: Boolean) = if (b) "1" else "0"

        /** Reads [encode]'s text; null for missing or unreadable text. */
        fun parse(text: String?): AlarmDiag? = runCatching {
            if (text.isNullOrEmpty()) return null
            val f = text.split(";").associate { it.substringBefore("=") to it.substringAfter("=") }
            val vol = f.getValue("v").split("/")
            AlarmDiag(
                screenOn = f["s"] == "1",
                volume = vol[0].toInt(),
                volumeMax = vol[1].toInt(),
                alarmStream = f["a"] == "1",
                focusGranted = f["f"] == "1",
                doNotDisturb = f["d"] == "1",
                clips = f["c"].orEmpty().split(",").filter { it.isNotEmpty() }.map {
                    val p = it.split(":")
                    Clip(End.valueOf(p[0]), p[1].toLong(), p[2].toInt(), p.getOrElse(3) { "" })
                },
            )
        }.getOrNull()
    }
}
