package app.bp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.fail

class BpParserTest {

    private val parsed = BpParser.ParseResult.Parsed

    // ------------------------------------------------- required behaviors

    @Test
    fun `seven digit stream parses as 121 81 71`() {
        assertEquals(parsed(121, 81, 71), BpParser.parse("1218171"))
    }

    @Test
    fun `six digit stream parses as 98 69 59`() {
        assertEquals(parsed(98, 69, 59), BpParser.parse("986959"))
    }

    @Test
    fun `118666 has no valid delineation`() {
        assertTrue(
            "118/66/6 is not realistic, and no other split works either",
            BpParser.parse("118666") is BpParser.ParseResult.Unresolved,
        )
    }

    // ------------------------------------------------------------ waiting

    @Test
    fun `fewer than six digits is waiting`() {
        assertTrue(BpParser.parse("") is BpParser.ParseResult.Waiting)
        assertTrue(BpParser.parse("12345") is BpParser.ParseResult.Waiting)
    }

    @Test
    fun `ten or more digits is too long`() {
        assertTrue(BpParser.parse("1234567890") is BpParser.ParseResult.TooLong)
        assertTrue(BpParser.parse("12345678901") is BpParser.ParseResult.TooLong)
    }

    // ----------------------------------------------------------- ranges

    @Test
    fun `boundary readings are accepted`() {
        assertEquals(parsed(70, 40, 30), BpParser.parse("704030"))
        assertEquals(parsed(250, 150, 220), BpParser.parse("250150220"))
    }

    @Test
    fun `out of range readings are rejected`() {
        assertTrue(BpParser.parse("2605050") is BpParser.ParseResult.Unresolved) // sys 260
        assertTrue(BpParser.parse("1202650") is BpParser.ParseResult.Unresolved) // dia 26
        assertTrue(BpParser.parse("1208023") is BpParser.ParseResult.Unresolved) // hr 23
    }

    @Test
    fun `systolic must strictly exceed diastolic`() {
        assertTrue(BpParser.parse("909050") is BpParser.ParseResult.Unresolved)
        assertEquals(parsed(90, 80, 50), BpParser.parse("908050"))
    }

    @Test
    fun `leading zeros read as numbers`() {
        assertEquals(parsed(90, 80, 60), BpParser.parse("0908060"))
    }

    @Test
    fun `non digit characters are ignored`() {
        assertEquals(parsed(98, 69, 59), BpParser.parse("9 8-6 9/5 9"))
    }

    // ------------------------------------------------------ uniqueness

    @Test
    fun `parsing is never ambiguous across a wide fuzz run`() {
        val rnd = java.util.Random(42)
        repeat(300_000) {
            val n = 6 + rnd.nextInt(4)
            val s = (1..n).joinToString("") { rnd.nextInt(10).toString() }
            when (val r = BpParser.parse(s)) {
                is BpParser.ParseResult.Parsed -> {
                    assertTrue(r.sys in BpParser.Ranges.systolic)
                    assertTrue(r.dia in BpParser.Ranges.diastolic)
                    assertTrue(r.hr in BpParser.Ranges.heartRate)
                    assertTrue(r.sys > r.dia)
                }
                is BpParser.ParseResult.Ambiguous -> fail("ambiguous parse for $s: $r")
                else -> Unit
            }
        }
    }
}
