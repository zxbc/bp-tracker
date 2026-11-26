package app.bp.domain

/**
 * Parses a raw digit stream (as typed on the entry numpad) into a blood
 * pressure reading: systolic / diastolic / heart rate.
 *
 * Pure Kotlin — no Android imports — so it is trivially unit-testable.
 * See docs/PLAN.md §4 for the full spec and the uniqueness argument.
 */
object BpParser {

    /**
     * Realism ranges. Deliberately wide so real readings (hypertensive,
     * elderly, resting bradycardia) are never rejected — only absurd
     * values fail. This is the single place to tune acceptance.
     */
    object Ranges {
        val systolic = 70..250
        val diastolic = 30..150
        val heartRate = 30..220
    }

    /** Result of parsing the current digit stream. */
    sealed interface ParseResult {
        /** Fewer than 6 digits: not enough to delineate yet. */
        data class Waiting(val digits: String) : ParseResult

        /** The whole stream delineates into exactly one valid reading. */
        data class Parsed(val sys: Int, val dia: Int, val hr: Int) : ParseResult

        /** 6–9 digits, but no delineation yields a realistic reading. */
        data class Unresolved(val digits: String) : ParseResult

        /** More than 9 digits: can never be valid. */
        data class TooLong(val digits: String) : ParseResult

        /**
         * More than one delineation is valid. Provably unreachable with
         * [Ranges] (PLAN §4.3) but handled defensively so a future range
         * tweak can never silently pick the wrong split.
         */
        data class Ambiguous(val digits: String, val candidates: List<Parsed>) : ParseResult
    }

    private data class Split(val sysLen: Int, val diaLen: Int, val hrLen: Int)

    /** All ways to cut a stream of the given length into 2–3 digit parts. */
    private val splitsByLength: Map<Int, List<Split>> = mapOf(
        6 to listOf(Split(2, 2, 2)),
        7 to listOf(Split(2, 2, 3), Split(2, 3, 2), Split(3, 2, 2)),
        8 to listOf(Split(2, 3, 3), Split(3, 2, 3), Split(3, 3, 2)),
        9 to listOf(Split(3, 3, 3)),
    )

    /**
     * Parse [raw] into a reading. Non-digit characters are ignored. O(1):
     * at most three splits exist for any input length.
     */
    fun parse(raw: String): ParseResult {
        val digits = raw.filter(Char::isDigit)
        return when (val n = digits.length) {
            in 0..5 -> ParseResult.Waiting(digits)
            in 10..Int.MAX_VALUE -> ParseResult.TooLong(digits)
            else -> {
                val valid = splitsByLength.getValue(n).mapNotNull { s ->
                    val sys = digits.take(s.sysLen).toIntOrNull() ?: return@mapNotNull null
                    val dia = digits.substring(s.sysLen, s.sysLen + s.diaLen).toIntOrNull()
                        ?: return@mapNotNull null
                    val hr = digits.substring(s.sysLen + s.diaLen).toIntOrNull()
                        ?: return@mapNotNull null
                    if (sys in Ranges.systolic &&
                        dia in Ranges.diastolic &&
                        hr in Ranges.heartRate &&
                        sys > dia
                    ) ParseResult.Parsed(sys, dia, hr) else null
                }
                when {
                    valid.isEmpty() -> ParseResult.Unresolved(digits)
                    valid.size == 1 -> valid.first()
                    else -> ParseResult.Ambiguous(digits, valid)
                }
            }
        }
    }
}
