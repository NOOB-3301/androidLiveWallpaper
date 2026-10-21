package com.haiku.wall.ui

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/** The few fields the app UI needs from a plan, without parsing its thousands of marks. */
data class PlanMeta(
    val haiku: List<String>,
    val ground: String,
    val inks: List<String>,
    val totalMs: Long,
)

object PlanMetaReader {
    fun read(json: String): PlanMeta? = runCatching {
        JsonReader(StringReader(json)).use { reader ->
            var haiku = emptyList<String>()
            var ground = "#F3F0E8"
            var inks = emptyList<String>()
            var total = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "haiku" -> {
                        val lines = ArrayList<String>(3)
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonToken.STRING) lines.add(reader.nextString()) else reader.skipValue()
                        }
                        reader.endArray()
                        haiku = lines
                    }
                    "ground" -> if (reader.peek() == JsonToken.STRING) ground = reader.nextString() else reader.skipValue()
                    "inks" -> {
                        val list = ArrayList<String>(4)
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonToken.STRING) list.add(reader.nextString()) else reader.skipValue()
                        }
                        reader.endArray()
                        inks = list
                    }
                    "total" -> total = if (reader.peek() == JsonToken.NUMBER) reader.nextLong() else 0L
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            PlanMeta(haiku, ground, inks, total)
        }
    }.getOrNull()
}
