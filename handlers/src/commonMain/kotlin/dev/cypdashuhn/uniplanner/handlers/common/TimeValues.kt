package dev.cypdashuhn.uniplanner.handlers.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

sealed interface Moment {
    val text: String

    fun plus(duration: Duration): Moment?
}

data class DateMoment(
    val date: LocalDate,
) : Moment {
    override val text: String get() = date.toString()

    override fun plus(duration: Duration): Moment? =
        if (isWholeDays(duration)) {
            DateMoment(date.plus(duration.inWholeDays.toInt(), DateTimeUnit.DAY))
        } else {
            null
        }
}

data class InstantMoment(
    val instant: Instant,
) : Moment {
    override val text: String get() = instant.toString()

    override fun plus(duration: Duration): Moment = InstantMoment(instant + duration)
}

object TimeValues {
    fun moment(element: JsonElement?): Moment? = element.textOrNull()?.let(::parseMoment)

    fun duration(element: JsonElement?): Duration? = element.textOrNull()?.let(::parseDuration)

    fun momentValue(moment: Moment): JsonElement = JsonPrimitive(moment.text)

    fun durationValue(duration: Duration): JsonElement = JsonPrimitive(formatDuration(duration))

    fun endOf(
        start: Moment,
        duration: Duration,
    ): Moment? = start.plus(duration)

    fun sameMoment(
        left: Moment?,
        right: Moment?,
    ): Boolean =
        when {
            left == null || right == null -> false
            left is DateMoment && right is DateMoment -> left.date == right.date
            left is InstantMoment && right is InstantMoment -> left.instant == right.instant
            left is DateMoment && right is InstantMoment -> left.date.atStartOfDayIn(TimeZone.UTC) == right.instant
            left is InstantMoment && right is DateMoment -> left.instant == right.date.atStartOfDayIn(TimeZone.UTC)
            else -> false
        }

    fun durationBetween(
        start: Moment,
        end: Moment,
    ): Duration? =
        when {
            start is DateMoment && end is DateMoment -> (end.date.toEpochDays() - start.date.toEpochDays()).toDuration(DurationUnit.DAYS)
            start is InstantMoment && end is InstantMoment -> end.instant - start.instant
            start is DateMoment && end is InstantMoment -> end.instant - start.date.atStartOfDayIn(TimeZone.UTC)
            start is InstantMoment && end is DateMoment -> end.date.atStartOfDayIn(TimeZone.UTC) - start.instant
            else -> null
        }

    private fun parseMoment(value: String): Moment? =
        runCatching { Instant.parse(value) }.getOrNull()?.let(::InstantMoment)
            ?: runCatching { LocalDate.parse(value) }.getOrNull()?.let(::DateMoment)

    private fun parseDuration(value: String): Duration? = runCatching { Duration.parseIsoString(value) }.getOrNull()

    private fun formatDuration(duration: Duration): String =
        if (duration >= Duration.ZERO && isWholeDays(duration)) {
            "P${duration.inWholeDays}D"
        } else {
            duration.toIsoString()
        }
}

private fun isWholeDays(duration: Duration): Boolean = duration == duration.inWholeDays.toDuration(DurationUnit.DAYS)
