package dev.cypdashuhn.uniplanner.handlers.common

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.LocalDate

object TimeValues {
    fun date(element: JsonElement?): LocalDate? = element.textOrNull()?.let(::parseDate)

    fun duration(element: JsonElement?): Duration? = element.textOrNull()?.let(::parseDuration)

    fun dateValue(date: LocalDate): JsonElement = JsonPrimitive(date.toString())

    fun durationValue(duration: Duration): JsonElement = JsonPrimitive(formatDuration(duration))

    fun endOf(start: LocalDate, duration: Duration): LocalDate? =
        if (duration.seconds % SECONDS_PER_DAY == 0L) start.plusDays(duration.toDays()) else null

    fun durationBetween(start: LocalDate, end: LocalDate): Duration = Duration.ofDays(end.toEpochDay() - start.toEpochDay())

    private const val SECONDS_PER_DAY = 86_400L

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun parseDuration(value: String): Duration? = runCatching { Duration.parse(value) }.getOrNull()

    private fun formatDuration(duration: Duration): String =
        if (duration.seconds >= 0 && duration.seconds % SECONDS_PER_DAY == 0L) {
            "P${duration.toDays()}D"
        } else {
            duration.toString()
        }
}
