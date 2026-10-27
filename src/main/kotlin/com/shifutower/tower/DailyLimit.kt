package com.shifutower.tower

import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.data.PlayerRecord
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Controle de "1 desafio por dia" (o dia vira à meia-noite no fuso configurado). */
object DailyLimit {
    private val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun zone(): ZoneId = try {
        ZoneId.of(ConfigManager.tower.timezone)
    } catch (_: Exception) {
        ShifuTower.LOGGER.warn("Fuso horário inválido '{}', usando America/Sao_Paulo", ConfigManager.tower.timezone)
        ZoneId.of("America/Sao_Paulo")
    }

    fun today(): String = LocalDate.now(zone()).toString()

    fun todayDisplay(): String = LocalDate.now(zone()).format(DATE_BR)

    fun attemptsLeft(record: PlayerRecord): Int {
        val used = if (record.attemptDay == today()) record.attemptsUsed else 0
        return (ConfigManager.tower.attemptsPerDay - used).coerceAtLeast(0)
    }

    fun consume(record: PlayerRecord) {
        val day = today()
        if (record.attemptDay != day) {
            record.attemptDay = day
            record.attemptsUsed = 0
        }
        record.attemptsUsed++
    }

    fun refund(record: PlayerRecord) {
        if (record.attemptDay == today() && record.attemptsUsed > 0) record.attemptsUsed--
    }

    fun reset(record: PlayerRecord) {
        record.attemptDay = ""
        record.attemptsUsed = 0
    }

    fun millisUntilReset(): Long {
        val now = ZonedDateTime.now(zone())
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone())
        return Duration.between(now, midnight).toMillis()
    }
}
