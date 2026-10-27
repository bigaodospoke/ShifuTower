package com.shifutower.tower

import com.shifutower.config.TrainerArchetype
import com.shifutower.data.SavedPos
import java.util.UUID

enum class Phase {
    /** Jogador acabou de chegar no andar; o treinador ainda vai falar. */
    ARRIVING,
    /** Balão de apresentação do treinador na tela. */
    INTRO,
    /** Batalha em andamento. */
    BATTLE,
    /** Batalha acabou; treinador comentando o resultado. */
    AFTER_BATTLE,
    /** Chefe derrotado: festa antes de voltar. */
    CELEBRATION,
}

/** Um desafio ativo de um jogador. */
class TowerRun(
    val playerId: UUID,
    val playerName: String,
    val slot: Int,
    val startedAt: Long,
    val returnPos: SavedPos,
) {
    var floor = 1
    var phase = Phase.ARRIVING
    var timer = 0
    var npcId: UUID? = null
    var npcEntityId = -1
    var battleId: UUID? = null
    var battleWatchdog = 0
    var bpEarned = 0
    var archetype: TrainerArchetype? = null
    var trainerName = ""
    var tierName = ""
    var boss = false
    var wonLast = false
    var finalTimeMs = 0L
    var newRecord = false
    var trophyGiven = false
}
