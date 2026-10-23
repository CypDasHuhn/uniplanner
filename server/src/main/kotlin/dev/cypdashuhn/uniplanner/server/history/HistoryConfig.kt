package dev.cypdashuhn.uniplanner.server.history

import java.nio.file.Path
import java.time.ZoneId

data class HistoryConfig(
    val enabled: Boolean = true,
    val directory: Path,
    val timeZone: ZoneId = ZoneId.of(DEFAULT_TIME_ZONE),
    val retentionDays: Long = DEFAULT_RETENTION_DAYS,
) {
    init {
        require(retentionDays >= 0) { "history retention days must not be negative" }
    }

    companion object {
        const val DEFAULT_DIRECTORY = ".upl-history"
        const val DEFAULT_TIME_ZONE = "UTC"
        const val DEFAULT_RETENTION_DAYS = 90L

        fun defaults(vault: Path): HistoryConfig =
            HistoryConfig(directory = vault.resolve(DEFAULT_DIRECTORY).normalize())
    }
}
