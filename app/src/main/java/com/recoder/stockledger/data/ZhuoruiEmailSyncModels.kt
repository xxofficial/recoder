package com.recoder.stockledger.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ZhuoruiEmailSyncConfig(
    val imapHost: String = "",
    val imapPort: String = "993",
    val account: String = "",
    val password: String = "",
    val folder: String = "INBOX",
    val targetLedgerId: Long = 1L,
) {
    fun validationMessage(): String? = when {
        imapHost.isBlank() -> "请填写 IMAP 地址"
        imapPort.toIntOrNull() == null -> "请填写有效的 IMAP 端口"
        account.isBlank() -> "请填写邮箱账号"
        password.isBlank() -> "请填写授权码或密码"
        folder.isBlank() -> "请填写邮箱文件夹"
        else -> null
    }

    fun isComplete(): Boolean = validationMessage() == null

    fun resolvedPort(): Int = imapPort.toIntOrNull() ?: 993
}

data class ZhuoruiEmailManualSyncOptions(
    val fetchCount: String = "80",
    val earliestReceivedAt: String = "",
) {
    fun validationMessage(): String? = when {
        fetchCount.toIntOrNull() == null || resolvedFetchCount() <= 0 -> "请填写有效的拉取封数"
        earliestReceivedAt.isNotBlank() && resolvedEarliestReceivedAtMillis() == null -> "请填写有效的最早到达时间"
        else -> null
    }

    fun resolvedFetchCount(): Int = fetchCount.toIntOrNull()?.coerceIn(1, 500) ?: 80

    fun resolvedEarliestReceivedAtMillis(): Long? {
        val raw = earliestReceivedAt.trim()
        if (raw.isBlank()) return null
        dateTimeParsers.forEach { formatter ->
            runCatching {
                return LocalDateTime.parse(raw, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        runCatching {
            return LocalDate.parse(raw, dateParser)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        return null
    }

    private companion object {
        val dateParser: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val dateTimeParsers: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )
    }
}
