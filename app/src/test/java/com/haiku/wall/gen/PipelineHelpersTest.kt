package com.haiku.wall.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PipelineHelpersTest {
    @Test fun stripFences_takesFencedBody() {
        assertEquals("ground('#fff');", stripFences("Here you go:\n```javascript\nground('#fff');\n```\nEnjoy"))
        assertEquals("a();\nb();", stripFences("  a();\nb();  "))
        assertEquals("a();", stripFences("```js\na();")) // truncated reply, no closing fence
    }

    @Test fun extractJson_findsObjectInProse() {
        assertEquals("""{"a":{"b":1}}""", extractJson("Sure!\n```json\n{\"a\":{\"b\":1}}\n```"))
        assertEquals("""{"x":2}""", extractJson("""the answer is {"x":2} ok"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractJson_throwsWithoutObject() {
        extractJson("no json here")
    }

    @Test fun scores_revisionAndRanking() {
        val good = Scores(5.0, 5.0, 5.0, 4.0, 4.0, "")
        val pretty = Scores(5.0, 5.0, 4.0, 5.0, 5.0, "") // avg 4.8 but legibility 4
        val weak = Scores(3.0, 4.0, 5.0, 3.0, 3.0, "")
        assertEquals(4.6, good.avg, 1e-9)
        assertFalse(good.needsRevision)
        assertTrue(pretty.needsRevision)
        assertTrue(weak.needsRevision)
        assertTrue(good.beats(pretty)) // legible beats higher average
        assertTrue(good.beats(weak))
        assertFalse(weak.beats(good))
    }

    @Test fun pickExamples_prefersSameMediumAndCaps() {
        val ex = listOf("chalk", "pencil", "felt marker", "ballpoint", "gold gel", "crayon")
            .map { "// medium: $it | layout: ring burst | haiku: a / b / c\nline();" }
        val picked = pickExamples(ex, "graphite pencil", rnd = Random(1))
        assertEquals(4, picked.size)
        assertEquals("pencil", exampleMedium(picked[0]))
        assertEquals("goldgel", exampleMedium(pickExamples(ex, "Gold gel on dark", rnd = Random(2))[0]))
        assertEquals(emptyList<String>(), pickExamples(emptyList(), "chalk"))
    }

    @Test fun assembleSystemPrompt_fillsMarkers() {
        val s = assembleSystemPrompt("A\n{{KIT_API}}\nB\n{{EXAMPLES}}\nC", " kit api ", listOf("// medium: chalk\nx();"))
        assertEquals("A\nkit api\nB\n### Example 1\n// medium: chalk\nx();\nC", s)
        assertFalse("{{" in assembleSystemPrompt("{{KIT_API}}{{EXAMPLES}}", "k", emptyList()))
    }

    @Test fun fill_replacesAllPlaceholders() {
        assertEquals("rain at night, rain", fill("{{theme}} at {{timeOfDay}}, {{theme}}", mapOf("theme" to "rain", "timeOfDay" to "night")))
        assertEquals("winter", seasonOf(1))
        assertEquals("night", timeOfDayOf(23))
    }
}
