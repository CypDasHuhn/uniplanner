package dev.cypdashuhn.uniplanner.handlers.gantt

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.common.TimeValues

object GanttFields {
    val progress = FieldKey("gantt.progress")
}

class GanttHandler : Handler {
    override val namespace = "gantt"

    override fun ownedFields(): Set<FieldKey> = setOf(Std.timeStart, Std.timeEnd, Std.timeDuration, GanttFields.progress)

    override fun subscribedFields(): Set<FieldKey> = setOf(Std.timeStart, Std.timeEnd, Std.timeDuration)

    override fun onFieldChanged(
        change: FieldChange,
        document: DocumentView,
    ): List<Mutation> =
        when (change.field) {
            Std.timeStart, Std.timeDuration -> derivedEnd(document)
            Std.timeEnd -> derivedDuration(document)
            else -> emptyList()
        }

    private fun derivedEnd(document: DocumentView): List<Mutation> {
        val start = TimeValues.moment(document.value(Std.timeStart)) ?: return emptyList()
        val duration = TimeValues.duration(document.value(Std.timeDuration)) ?: return emptyList()
        val end = TimeValues.endOf(start, duration) ?: return emptyList()
        if (TimeValues.sameMoment(TimeValues.moment(document.value(Std.timeEnd)), end)) return emptyList()
        return listOf(Mutation(Std.timeEnd, TimeValues.momentValue(end)))
    }

    private fun derivedDuration(document: DocumentView): List<Mutation> {
        val start = TimeValues.moment(document.value(Std.timeStart)) ?: return emptyList()
        val end = TimeValues.moment(document.value(Std.timeEnd)) ?: return emptyList()
        val duration = TimeValues.durationBetween(start, end) ?: return emptyList()
        if (TimeValues.duration(document.value(Std.timeDuration)) == duration) return emptyList()
        return listOf(Mutation(Std.timeDuration, TimeValues.durationValue(duration)))
    }
}
