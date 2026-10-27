package com.shifutower.config

import com.google.gson.annotations.SerializedName

/** config/shifutower/torre.json */
class TowerConfig {
    @SerializedName("andares")
    var floors: Int = 10

    @SerializedName("tentativasPorDia")
    var attemptsPerDay: Int = 1

    /** Fuso usado para decidir quando "vira o dia" da tentativa diária. */
    @SerializedName("fusoHorario")
    var timezone: String = "America/Sao_Paulo"

    /** BP ganhos ao vencer cada andar (índice 0 = andar 1). Se faltar valor, usa o último + 5 por andar. */
    @SerializedName("bpPorAndar")
    var bpPerFloor: List<Int> = listOf(3, 4, 5, 6, 8, 10, 12, 15, 20, 40)

    @SerializedName("bpBonusConclusao")
    var completionBonus: Int = 50

    /** true = ganha troféu a cada vitória sobre o chefe; false = só na primeira. */
    @SerializedName("trofeuEmTodaVitoria")
    var trophyEveryClear: Boolean = true

    /** 0 = desligado. Se > 0, todos os Pokémon (jogador e NPC) lutam nesse nível. */
    @SerializedName("nivelForcadoNaBatalha")
    var forcedBattleLevel: Int = 0

    /** Espécies que o jogador NÃO pode levar para a torre (ex.: "mewtwo"). */
    @SerializedName("especiesProibidas")
    var bannedSpecies: List<String> = emptyList()

    /** 0 = sem limite. Pokémon do jogador acima desse nível bloqueiam o desafio. */
    @SerializedName("nivelMaximoJogador")
    var maxPlayerLevel: Int = 0

    @SerializedName("tamanhoRanking")
    var rankingSize: Int = 10

    @SerializedName("anunciarConclusaoNoChat")
    var broadcastClear: Boolean = true

    /** Se true, usa primeiro Pokémon que têm modelo no Cobblemon (os demais só completam o time se faltar). */
    @SerializedName("preferirPokemonComModelo")
    var preferImplemented: Boolean = true

    @SerializedName("segundosAntesDaBatalha")
    var secondsBeforeBattle: Int = 4

    @SerializedName("segundosAposBatalha")
    var secondsAfterBattle: Int = 4

    @SerializedName("faixas")
    var tiers: List<TierConfig> = defaultTiers()

    companion object {
        fun defaultTiers(): List<TierConfig> = listOf(
            TierConfig("Iniciante", 1, 2, 3, 55, 60, 10, 20, false, 1, 0.01, "iniciante"),
            TierConfig("Intermediário", 3, 4, 4, 65, 70, 15, 25, false, 2, 0.01, "intermediario"),
            TierConfig("Avançado", 5, 6, 5, 75, 80, 20, 31, true, 3, 0.02, "avancado"),
            TierConfig("Elite", 7, 8, 6, 85, 90, 25, 31, true, 4, 0.02, "elite"),
            TierConfig("Mestre", 9, 9, 6, 95, 95, 31, 31, true, 5, 0.03, "mestre"),
            TierConfig("Chefe", 10, 10, 6, 100, 100, 31, 31, true, 5, 0.05, "lendarios_uber", chefe = true),
        )
    }
}

class TierConfig(
    @SerializedName("nome") var name: String = "Faixa",
    @SerializedName("andarInicial") var fromFloor: Int = 1,
    @SerializedName("andarFinal") var toFloor: Int = 1,
    @SerializedName("tamanhoTime") var teamSize: Int = 3,
    @SerializedName("nivelMinimo") var levelMin: Int = 50,
    @SerializedName("nivelMaximo") var levelMax: Int = 50,
    @SerializedName("ivMinimo") var ivMin: Int = 0,
    @SerializedName("ivMaximo") var ivMax: Int = 31,
    /** Aplica os EVs sugeridos de cada set do grupo. */
    @SerializedName("usarEVs") var useEvs: Boolean = false,
    /** Inteligência da IA do Cobblemon (0 a 5). */
    @SerializedName("habilidadeIA") var aiSkill: Int = 3,
    @SerializedName("chanceShiny") var shinyChance: Double = 0.0,
    /** Nome do grupo em times.json. */
    @SerializedName("grupo") var pool: String = "iniciante",
    /** Faixa do chefe final (usa o treinador marcado como chefe). */
    @SerializedName("chefe") var chefe: Boolean = false,
)
