package dev.cypdashuhn.uniplanner.handlers.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object TimeValues {
    fun date(element: JsonElement?): LocalDate? = element.textOrNull()?.let(::parseDate)

    fun duration(element: JsonElement?): Duration? = element.textOrNull()?.let(::parseDuration)

    fun dateValue(date: LocalDate): JsonElement = JsonPrimitive(date.toString())

    fun durationValue(duration: Duration): JsonElement = JsonPrimitive(formatDuration(duration))

    fun endOf(
        start: LocalDate,
        duration: Duration,
    ): LocalDate? = if (isWholeDays(duration)) start.plus(duration.inWholeDays.toInt(), DateTimeUnit.DAY) else null

    fun durationBetween(
        start: LocalDate,
        end: LocalDate,
    ): Duration = (end.toEpochDays() - start.toEpochDays()).toDuration(DurationUnit.DAYS)

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun parseDuration(value: String): Duration? = runCatching { Duration.parseIsoString(value) }.getOrNull()

    private fun formatDuration(duration: Duration): String =
        if (duration >= Duration.ZERO && isWholeDays(duration)) {
            "P${duration.inWholeDays}D"
        } else {
            duration.toIsoString()
        }

    private fun isWholeDays(duration: Duration): Boolean = duration == duration.inWholeDays.toDuration(DurationUnit.DAYS)
}
