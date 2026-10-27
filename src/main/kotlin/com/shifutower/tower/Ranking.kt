package com.shifutower.tower

import com.shifutower.data.PlayerRecord
import com.shifutower.data.TowerStorage
import java.util.UUID

data class RankEntry(val uuid: UUID, val name: String, val timeMs: Long, val date: String)

/** Ranking pelo melhor tempo de conclusão (menor tempo = primeiro lugar). */
object Ranking {
    fun sorted(): List<RankEntry> =
        TowerStorage.all().entries
            .filter { it.value.bestTimeMs > 0 }
            .sortedWith(compareBy<Map.Entry<String, PlayerRecord>> { it.value.bestTimeMs }.thenBy { it.value.bestTimeAt })
            .map { RankEntry(UUID.fromString(it.key), it.value.name, it.value.bestTimeMs, it.value.bestTimeDate) }

    fun top(n: Int): List<RankEntry> = sorted().take(n)

    /** Posição (1 = primeiro) ou -1 se ainda não concluiu a torre. */
    fun positionOf(uuid: UUID): Int {
        val index = sorted().indexOfFirst { it.uuid == uuid }
        return if (index < 0) -1 else index + 1
    }
}
