package com.haiku.wall.gen

import kotlin.random.Random

// Pure helpers for Pipeline: plain Kotlin only (no Android, no org.json) so they run in JVM unit tests.

private val FENCE = Regex("```[\\w-]*[ \\t]*\\r?\\n(.*?)\\r?\\n?```", RegexOption.DOT_MATCHES_ALL)

/** Content of the first ``` fenced block if any, else the trimmed reply; drops a dangling opening fence (truncated reply). */
fun stripFences(raw: String): String {
    val t = raw.trim()
    FENCE.find(t)?.let { return it.groupValues[1].trim() }
    return if (t.startsWith("```")) t.substringAfter('\n', "").trim() else t
}

/** The outermost {...} in a model reply (prose and fences tolerated). */
fun extractJson(raw: String): String {
    val t = stripFences(raw)
    val a = t.indexOf('{')
    val b = t.lastIndexOf('}')
    require(a >= 0 && b > a) { "no JSON object in reply: ${t.take(120)}" }
    return t.substring(a, b + 1)
}

/** Replaces each {{key}} with its value. */
fun fill(template: String, vars: Map<String, String>): String =
    vars.entries.fold(template) { t, (k, v) -> t.replace("{{$k}}", v) }

fun assembleSystemPrompt(template: String, kitApi: String, examples: List<String>): String = fill(
    template, mapOf(
        "KIT_API" to kitApi.trim(),
        "EXAMPLES" to if (examples.isEmpty()) "(none available this time)"
        else examples.mapIndexed { i, e -> "### Example ${i + 1}\n${e.trim()}" }.joinToString("\n\n"),
    )
)

/** `medium` from an example's line-1 header `// medium: <name> | layout: ... | haiku: ...`, letters only, lowercase. */
fun exampleMedium(program: String): String =
    Regex("medium:\\s*([^|\\n]*)").find(program.lineSequence().firstOrNull().orEmpty())
        ?.groupValues?.get(1).orEmpty().norm()

/** Up to [n] examples, same medium first (loose match: "pencil" ~ "graphite pencil"), rest random. */
fun pickExamples(all: List<String>, medium: String, n: Int = 4, rnd: Random = Random): List<String> {
    val m = medium.norm()
    val (same, other) = all.shuffled(rnd).partition {
        val e = exampleMedium(it)
        m.isNotEmpty() && e.isNotEmpty() && (e in m || m in e)
    }
    return (same + other).take(n)
}

private fun String.norm() = lowercase().filter { it.isLetter() }

data class Scores(
    val concept: Double, val cohesion: Double, val legibility: Double, val balance: Double, val craft: Double,
    val critique: String,
) {
    val avg get() = (concept + cohesion + legibility + balance + craft) / 5
    val needsRevision get() = avg < 4 || legibility < 5
    /** Readable text wins over everything, then the average. */
    fun beats(o: Scores) = compareValuesBy(this, o, { it.legibility >= 5 }, { it.avg }) > 0
    override fun toString() =
        "concept $concept, cohesion $cohesion, legibility $legibility, balance $balance, craft $craft (avg ${"%.1f".format(avg)})"
}

fun conceptPrompt(conceptJson: String, w: Int, h: Int, seconds: Int): String = """
Concept from the art director:
$conceptJson

Canvas: W=$w, H=$h, portrait. SAFE: x 60..1020, y ${(h * 0.12).toInt()}..${(h * 0.88).toInt()}. The drawing plays for $seconds s.
Draw this concept in its medium, palette and layout. Reply with the JavaScript program body only.
""".trim()

fun repairPrompt(error: String) = """
The kit threw this error when running your program:
$error

Reply with the corrected full program (JavaScript only, no fences, no commentary). Keep the same drawing and fix what the error requires; stay well under the kit's caps.
""".trim()

fun revisionPrompt(s: Scores) = """
An art editor looked at a thumbnail of the result. Scores out of 5: $s.
Critique: ${s.critique}

Rewrite the full program to fix this. Keep the haiku, medium and palette; keep the poem fully legible with a clear halo gap. Reply with the JavaScript program body only.
""".trim()

// shortcut: northern-hemisphere seasons, take a hemisphere/locale hint from Prefs if southern users complain
fun seasonOf(month: Int) = when (month) {
    12, 1, 2 -> "winter"; in 3..5 -> "spring"; in 6..8 -> "summer"; else -> "autumn"
}

fun timeOfDayOf(hour: Int) = when (hour) {
    in 5..10 -> "morning"; in 11..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night"
}
