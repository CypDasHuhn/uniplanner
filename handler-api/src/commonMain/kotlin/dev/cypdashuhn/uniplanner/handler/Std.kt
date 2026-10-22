package dev.cypdashuhn.uniplanner.handler

object Std {
    val name = FieldKey("std.name")
    val priority = FieldKey("std.priority")
    val status = FieldKey("std.status")
    val statusClosed = FieldKey("std.status.closed")
    val time = FieldKey("std.time")
    val timeStart = FieldKey("std.time.start")
    val timeEnd = FieldKey("std.time.end")
    val timeDuration = FieldKey("std.time.duration")
    val timeDeadline = FieldKey("std.time.deadline")
    val timeEstimate = FieldKey("std.time.estimate")
    val relations = FieldKey("std.relations")
    val relationsParent = FieldKey("std.relations.parent")
    val relationsDependsOn = FieldKey("std.relations.depends-on")
    val tags = FieldKey("std.tags")
    val tagColors = FieldKey("std.tag.colors")
}
