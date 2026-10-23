package dev.cypdashuhn.uniplanner.server.history

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryStoreTest {
    private val name = FieldKey("std.name")
    private val zone = ZoneId.of("UTC")

    @Test
    fun `writes an applied mutation with request and task snapshots`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)
        val before = container()
        val request =
            MutationRequest(
                requester = "weekplanner",
                mutations = listOf(Mutation(name, JsonPrimitive("Changed"), "t1")),
                createTasks = listOf(NewTask("t3", mapOf(name to JsonPrimitive("New")))),
                removeTasks = listOf("t2"),
            )
        val result =
            MutationResult.Applied(
                document = container("Changed"),
                changes = listOf(FieldChange(name, JsonPrimitive("First"), JsonPrimitive("Changed"), "t1")),
            )

        store.recordMutation(root.resolve("week.upl.json"), "alice", before, request, result)

        val entry = onlyEntry(root, "2026-05-01")
        assertEquals(1, entry["v"]?.jsonPrimitive?.content?.toInt())
        assertEquals("2026-05-01T12:00:00Z", entry.string("timestamp"))
        assertEquals("mutation", entry.string("op"))
        assertEquals("applied", entry.string("status"))
        assertEquals("week.upl.json", entry.string("path"))
        assertEquals("alice", entry.string("principal"))
        assertEquals("weekplanner", entry.string("requester"))
        assertFalse(entry.containsKey("reason"))

        val document = entry.getValue("document").jsonObject
        assertEquals("week", document.string("id"))
        assertEquals("container", document.string("kind"))
        assertEquals(2, document["taskCount"]?.jsonPrimitive?.content?.toInt())

        val requestJson = entry.getValue("request").jsonObject
        assertEquals("weekplanner", requestJson.string("requester"))
        val mutation =
            requestJson
                .getValue("mutations")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("std.name", mutation.string("field"))
        assertEquals("t1", mutation.string("target"))
        assertEquals("Changed", mutation.string("value"))
        val created =
            requestJson
                .getValue("createTasks")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("t3", created.string("id"))
        assertEquals("New", created.getValue("fields").jsonObject.string("std.name"))
        assertEquals(listOf("t2"), requestJson.getValue("removeTasks").jsonArray.map { it.jsonPrimitive.content })

        val change =
            entry
                .getValue("changes")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("std.name", change.string("field"))
        assertEquals("t1", change.string("target"))
        assertEquals("First", change.string("oldValue"))
        assertEquals("Changed", change.string("newValue"))
        assertEquals(listOf("t3"), entry.getValue("createdTaskIds").jsonArray.map { it.jsonPrimitive.content })

        val removed =
            entry
                .getValue("removedTasks")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("t2", removed.string("id"))
        assertEquals("Second", removed.string("std.name"))
    }

    @Test
    fun `writes a rejected mutation with its reason and no changes`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)
        val request = MutationRequest("weekplanner", listOf(Mutation(name, JsonPrimitive("Changed"), "missing")))

        store.recordMutation(
            root.resolve("week.upl.json"),
            "alice",
            container(),
            request,
            MutationResult.Rejected("unknown task target: missing"),
        )

        val entry = onlyEntry(root, "2026-05-01")
        assertEquals("rejected", entry.string("status"))
        assertEquals("unknown task target: missing", entry.string("reason"))
        assertTrue(entry.getValue("changes").jsonArray.isEmpty())
    }

    @Test
    fun `writes a replacement with before and after summaries only`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)

        store.recordReplace(
            root.resolve("week.upl.json"),
            "alice",
            before = container(),
            after = container("Changed"),
            request = MutationRequest("import", emptyList()),
        )

        val entry = onlyEntry(root, "2026-05-01")
        assertEquals("replace", entry.string("op"))
        assertEquals("applied", entry.string("status"))
        assertEquals("import", entry.string("requester"))
        val replacement = entry.getValue("replacement").jsonObject
        assertEquals("week", replacement.getValue("before").jsonObject.string("id"))
        assertEquals("week", replacement.getValue("after").jsonObject.string("id"))
        assertTrue(entry.getValue("changes").jsonArray.isEmpty())
        assertFalse("tasks" in entry)
    }

    @Test
    fun `records a missing replacement before as null`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)

        store.recordReplace(
            root.resolve("new.upl.json"),
            "alice",
            before = null,
            after = UplDocument("new", DocumentKind.CONTAINER, emptyMap()),
            request = MutationRequest("import", emptyList()),
        )

        val entry = onlyEntry(root, "2026-05-01")
        assertTrue(entry.getValue("replacement").jsonObject["before"] is JsonNull)
    }

    @Test
    fun `uses the configured time zone for the daily file`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T23:30:00Z"))
        val store = store(root, clock, timeZone = ZoneId.of("Europe/Berlin"))

        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))

        assertTrue(Files.isRegularFile(root.resolve(".upl-history/2026-05-02.jsonl")))
        assertFalse(Files.exists(root.resolve(".upl-history/2026-05-01.jsonl")))
    }

    @Test
    fun `lists available days newest first with counts`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)

        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))
        clock.instant = Instant.parse("2026-05-02T12:00:00Z")
        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))
        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))

        val days = store.days()

        assertEquals(listOf(LocalDate.parse("2026-05-02"), LocalDate.parse("2026-05-01")), days.map { it.date })
        assertEquals(listOf(2, 1), days.map { it.count })
    }

    @Test
    fun `reads entries newest first and paginates with a cursor`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)

        listOf("First", "Second", "Third").forEach { value ->
            val result =
                MutationResult.Applied(
                    document = task(value),
                    changes = listOf(FieldChange(name, null, JsonPrimitive(value))),
                )
            store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), result)
        }

        val all = store.entries(HistoryQuery(LocalDate.parse("2026-05-01")))
        assertEquals(listOf("Third", "Second", "First"), all.map { it.changeValue() })

        val firstPage = store.entries(HistoryQuery(LocalDate.parse("2026-05-01"), limit = 2))
        assertEquals(listOf("Third", "Second"), firstPage.map { it.changeValue() })

        val secondPage =
            store.entries(
                HistoryQuery(
                    date = LocalDate.parse("2026-05-01"),
                    limit = 2,
                    before = firstPage.last().string("id"),
                ),
            )
        assertEquals(listOf("First"), secondPage.map { it.changeValue() })
    }

    @Test
    fun `filters entries by path status op requester and rejects unknown cursors`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)
        val applied = MutationResult.Applied(task("Changed"), listOf(FieldChange(name, null, JsonPrimitive("Changed"))))

        store.recordMutation(root.resolve("first.upl.json"), "alice", task(), request("one"), applied)
        store.recordMutation(root.resolve("second.upl.json"), "bob", task(), request("two"), MutationResult.Rejected("no"))
        store.recordReplace(
            root.resolve("third.upl.json"),
            "bob",
            before = null,
            after = UplDocument("third", DocumentKind.CONTAINER, emptyMap()),
            request = MutationRequest("import", emptyList()),
        )

        val date = LocalDate.parse("2026-05-01")
        assertEquals(1, store.entries(HistoryQuery(date, path = "first.upl.json")).size)
        assertEquals(1, store.entries(HistoryQuery(date, status = "rejected")).size)
        assertEquals(2, store.entries(HistoryQuery(date, status = "applied")).size)
        assertEquals(1, store.entries(HistoryQuery(date, status = "applied", op = "replace")).size)
        assertEquals(1, store.entries(HistoryQuery(date, requester = "import")).size)
        assertEquals(1, store.entries(HistoryQuery(date, requester = "two")).size)

        assertFailsWith<IllegalArgumentException> {
            store.entries(HistoryQuery(date, before = "missing"))
        }
    }

    @Test
    fun `tolerates a partial trailing line and repairs it before appending`() {
        val root = Files.createTempDirectory("upl-history-store")
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)
        val directory = Files.createDirectories(root.resolve(".upl-history"))
        val file = directory.resolve("2026-05-01.jsonl")
        Files.writeString(file, """{"id":"complete"}""" + "\n" + """{"id":"partial"""")

        assertEquals(listOf("complete"), store.entries(HistoryQuery(LocalDate.parse("2026-05-01"))).map { it.string("id") })

        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))

        val lines = Files.readAllLines(file)
        assertEquals(3, lines.size)
        assertEquals("""{"id":"partial"""", lines[1])
        assertEquals("rejected", Json.parseToJsonElement(lines[2]).jsonObject.string("status"))

        val entries = store.entries(HistoryQuery(LocalDate.parse("2026-05-01")))
        assertEquals(2, entries.size)
        assertEquals("complete", entries.last().string("id"))
    }

    @Test
    fun `retention keeps current future boundary and unrelated files`() {
        val root = Files.createTempDirectory("upl-history-store")
        val directory = Files.createDirectories(root.resolve(".upl-history"))
        val today = LocalDate.parse("2026-05-10")
        val old = dailyFile(directory, today.minusDays(91))
        val boundary = dailyFile(directory, today.minusDays(90))
        val current = dailyFile(directory, today)
        val future = dailyFile(directory, today.plusDays(1))
        val unrelated = Files.writeString(directory.resolve("notes.txt"), "keep")
        val malformed = Files.writeString(directory.resolve("not-a-day.jsonl"), "keep")

        store(root, MutableClock(Instant.parse("2026-05-10T12:00:00Z")))

        assertFalse(Files.exists(old))
        assertTrue(Files.exists(boundary))
        assertTrue(Files.exists(current))
        assertTrue(Files.exists(future))
        assertTrue(Files.exists(unrelated))
        assertTrue(Files.exists(malformed))
    }

    @Test
    fun `retention zero keeps every daily file`() {
        val root = Files.createTempDirectory("upl-history-store")
        val directory = Files.createDirectories(root.resolve(".upl-history"))
        val today = LocalDate.parse("2026-05-10")
        val old = dailyFile(directory, today.minusYears(2))

        store(root, MutableClock(Instant.parse("2026-05-10T12:00:00Z")), retentionDays = 0)

        assertTrue(Files.exists(old))
    }

    @Test
    fun `prunes when the day rotates`() {
        val root = Files.createTempDirectory("upl-history-store")
        val directory = Files.createDirectories(root.resolve(".upl-history"))
        val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
        val store = store(root, clock)
        val expired = dailyFile(directory, LocalDate.parse("2026-01-01"))
        Files.writeString(expired, "expired\n")

        clock.instant = Instant.parse("2026-05-02T12:00:00Z")
        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))

        assertFalse(Files.exists(expired))
    }

    @Test
    fun `an append failure is logged and does not throw`() {
        val root = Files.createTempDirectory("upl-history-store")
        val store = store(root, MutableClock(Instant.parse("2026-05-01T12:00:00Z")))
        Files.delete(root.resolve(".upl-history"))
        Files.writeString(root.resolve(".upl-history"), "not a directory")
        val logger = LoggerFactory.getLogger(HistoryStore::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)

        try {
            store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))
        } finally {
            logger.detachAppender(appender)
        }

        assertTrue(appender.list.any { it.level == Level.ERROR })
    }

    @Test
    fun `startup rejects a history path that is not a directory`() {
        val root = Files.createTempDirectory("upl-history-store")
        Files.writeString(root.resolve(".upl-history"), "not a directory")

        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            store(root, MutableClock(Instant.parse("2026-05-01T12:00:00Z")))
        }
    }

    @Test
    fun `disabled history writes nothing`() {
        val root = Files.createTempDirectory("upl-history-store")
        val store = store(root, MutableClock(Instant.parse("2026-05-01T12:00:00Z")), enabled = false)

        store.recordMutation(root.resolve("task.upl.json"), "alice", task(), request(), MutationResult.Rejected("no"))

        assertFalse(Files.exists(root.resolve(".upl-history")))
        assertTrue(store.days().isEmpty())
        assertNull(store.entries(HistoryQuery(LocalDate.parse("2026-05-01"))).firstOrNull())
    }

    private fun onlyEntry(
        root: Path,
        day: String,
    ): JsonObject {
        val lines = Files.readAllLines(root.resolve(".upl-history/$day.jsonl"))
        assertEquals(1, lines.size)
        return Json.parseToJsonElement(lines.single()).jsonObject
    }

    private fun container(changedName: String? = null): UplDocument =
        UplDocument(
            "week",
            DocumentKind.CONTAINER,
            mapOf(name to JsonPrimitive("Week")),
            mapOf(
                "t1" to task(changedName ?: "First", "t1"),
                "t2" to task("Second", "t2"),
            ),
        )

    private fun task(
        value: String = "Task",
        id: String = "t1",
    ): UplDocument = UplDocument(id, DocumentKind.TASK, mapOf(name to JsonPrimitive(value)))

    private fun request(requester: String = "test"): MutationRequest =
        MutationRequest(requester, listOf(Mutation(name, JsonPrimitive("Changed"))))

    private fun store(
        root: Path,
        clock: Clock,
        timeZone: ZoneId = zone,
        retentionDays: Long = 90,
        enabled: Boolean = true,
    ): HistoryStore =
        HistoryStore(
            vaultRoot = root,
            config =
                HistoryConfig(
                    enabled = enabled,
                    directory = root.resolve(".upl-history"),
                    timeZone = timeZone,
                    retentionDays = retentionDays,
                ),
            clock = clock,
        )

    private fun dailyFile(
        directory: Path,
        day: LocalDate,
    ): Path = Files.writeString(directory.resolve("$day.jsonl"), "entry\n")

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.changeValue(): String? =
        getValue("changes")
            .jsonArray
            .single()
            .jsonObject
            .string("newValue")

    private class MutableClock(
        var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant

        companion object {
            private val zone = ZoneId.of("UTC")
        }
    }

    private fun JsonPrimitive(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
}
