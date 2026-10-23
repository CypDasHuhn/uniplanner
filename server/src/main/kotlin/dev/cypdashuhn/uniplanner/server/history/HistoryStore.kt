package dev.cypdashuhn.uniplanner.server.history

import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class HistoryDay(
    val date: LocalDate,
    val count: Int,
)

data class HistoryQuery(
    val date: LocalDate,
    val path: String? = null,
    val status: String? = null,
    val op: String? = null,
    val requester: String? = null,
    val limit: Int = DEFAULT_LIMIT,
    val before: String? = null,
) {
    init {
        require(limit in 1..MAX_LIMIT) { "history limit must be between 1 and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 500
    }
}

class HistoryStore(
    private val vaultRoot: Path,
    val config: HistoryConfig,
    private val clock: Clock = Clock.systemUTC(),
    private val logger: Logger = LoggerFactory.getLogger(HistoryStore::class.java),
) {
    private var activeDay: LocalDate? = null

    init {
        if (config.enabled) {
            Files.createDirectories(config.directory)
            require(Files.isDirectory(config.directory)) { "history directory is not a directory: ${config.directory}" }
            require(Files.isWritable(config.directory)) { "history directory is not writable: ${config.directory}" }
            val now = clock.instant()
            activeDay = dayOf(now)
            pruneSafely(now)
        }
    }

    fun today(): LocalDate = dayOf(clock.instant())

    //region (Records)
    @Synchronized
    fun recordMutation(
        path: Path,
        principal: String,
        before: UplDocument,
        request: MutationRequest,
        result: MutationResult,
    ) {
        if (!config.enabled) return

        try {
            val timestamp = clock.instant()
            val entry =
                HistoryEntries.mutation(
                    id = UUID.randomUUID().toString(),
                    timestamp = timestamp,
                    path = relativePath(path),
                    principal = principal,
                    before = before,
                    request = request,
                    result = result,
                )
            append(entry, timestamp)
        } catch (error: Exception) {
            logger.error("history append failed for $path", error)
        }
    }

    @Synchronized
    fun recordReplace(
        path: Path,
        principal: String,
        before: UplDocument?,
        after: UplDocument,
        request: MutationRequest,
    ) {
        if (!config.enabled) return

        try {
            val timestamp = clock.instant()
            val entry =
                HistoryEntries.replacement(
                    id = UUID.randomUUID().toString(),
                    timestamp = timestamp,
                    path = relativePath(path),
                    principal = principal,
                    before = before,
                    after = after,
                    request = request,
                )
            append(entry, timestamp)
        } catch (error: Exception) {
            logger.error("history append failed for $path", error)
        }
    }
    //endregion

    //region (Retention)
    @Synchronized
    fun prune(now: Instant) {
        if (!config.enabled) return
        if (config.retentionDays == 0L) return
        if (!Files.isDirectory(config.directory)) return

        val today = dayOf(now)
        val cutoff = today.minusDays(config.retentionDays)

        Files.list(config.directory).use { files ->
            files
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val day = dayOfFile(file.fileName.toString()) ?: return@forEach
                    if (day.isBefore(cutoff)) Files.deleteIfExists(file)
                }
        }
    }
    //endregion

    //region (Reads)
    fun days(): List<HistoryDay> {
        if (!Files.isDirectory(config.directory)) return emptyList()

        val files = Files.list(config.directory).use { it.toList() }
        return files
            .filter { Files.isRegularFile(it) }
            .mapNotNull { file ->
                val day = dayOfFile(file.fileName.toString()) ?: return@mapNotNull null
                HistoryDay(day, readEntries(file).size)
            }.sortedByDescending { it.date }
    }

    fun entries(query: HistoryQuery): List<JsonObject> {
        val matching =
            readEntries(dailyFile(query.date))
                .filter { matches(it, query) }
                .asReversed()

        val afterCursor =
            when (val cursor = query.before) {
                null -> {
                    matching
                }

                else -> {
                    val cursorIndex = matching.indexOfFirst { it.string("id") == cursor }
                    require(cursorIndex >= 0) { "unknown history cursor" }
                    matching.drop(cursorIndex + 1)
                }
            }

        return afterCursor.take(query.limit)
    }
    //endregion

    //region (Files)
    private fun append(
        entry: JsonObject,
        timestamp: Instant,
    ) {
        val day = dayOf(timestamp)
        rotate(day, timestamp)

        Files.createDirectories(config.directory)
        val file = dailyFile(day)
        ensureTrailingNewline(file)
        Files.writeString(
            file,
            entry.toString() + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun rotate(
        day: LocalDate,
        timestamp: Instant,
    ) {
        if (day == activeDay) return
        activeDay = day
        pruneSafely(timestamp)
    }

    private fun pruneSafely(now: Instant) {
        try {
            prune(now)
        } catch (error: Exception) {
            logger.error("history retention failed", error)
        }
    }

    private fun ensureTrailingNewline(file: Path) {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return

        val lastByte =
            Files.newByteChannel(file, StandardOpenOption.READ).use { channel ->
                channel.position(Files.size(file) - 1)
                val buffer = ByteBuffer.allocate(1)
                channel.read(buffer)
                buffer.array().first()
            }
        if (lastByte != '\n'.code.toByte()) {
            Files.writeString(
                file,
                "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun readEntries(file: Path): List<JsonObject> {
        if (!Files.isRegularFile(file)) return emptyList()

        return Files
            .readString(file, StandardCharsets.UTF_8)
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() }
            .toList()
    }
    //endregion

    //region (Helpers)
    private fun matches(
        entry: JsonObject,
        query: HistoryQuery,
    ): Boolean {
        val pathMatches = query.path == null || entry.string("path") == query.path
        val statusMatches = query.status == null || entry.string("status") == query.status
        val opMatches = query.op == null || entry.string("op") == query.op
        val requesterMatches = query.requester == null || entry.string("requester") == query.requester
        return pathMatches && statusMatches && opMatches && requesterMatches
    }

    private fun dailyFile(day: LocalDate): Path = config.directory.resolve("$day.jsonl")

    private fun dayOf(instant: Instant): LocalDate = LocalDate.ofInstant(instant, config.timeZone)

    private fun dayOfFile(name: String): LocalDate? {
        if (!DAILY_FILE.matches(name)) return null
        return runCatching { LocalDate.parse(name.removeSuffix(".jsonl")) }.getOrNull()
    }

    private fun relativePath(path: Path): String =
        vaultRoot
            .toAbsolutePath()
            .normalize()
            .relativize(path.toAbsolutePath().normalize())
            .toString()
            .replace(File.separatorChar, '/')

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    //endregion

    companion object {
        private val DAILY_FILE = Regex("""\d{4}-\d{2}-\d{2}\.jsonl""")
    }
}
