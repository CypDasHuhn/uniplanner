package dev.cypdashuhn.uniplanner.handlers

import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handlers.dayplanner.DayplannerHandler
import dev.cypdashuhn.uniplanner.handlers.gantt.GanttHandler
import dev.cypdashuhn.uniplanner.handlers.kanban.KanbanHandler

object BuiltinHandlers {
    fun all(): List<Handler> = listOf(KanbanHandler(), DayplannerHandler(), GanttHandler())
}
