package com.shifutower.config

import com.google.gson.annotations.SerializedName

/**
 * Um set possível para um Pokémon dos treinadores (config/shifutower/times.json).
 * A cada desafio o mod sorteia 4 golpes de [moves], um item, uma natureza e uma habilidade.
 */
class PoolEntry {
    @SerializedName("especie")
    var species: String = ""

    @SerializedName("golpes")
    var moves: List<String> = emptyList()

    @SerializedName("itens")
    var items: List<String> = emptyList()

    @SerializedName("naturezas")
    var natures: List<String> = emptyList()

    /** Vazio = o Cobblemon escolhe uma habilidade legal da espécie. */
    @SerializedName("habilidades")
    var abilities: List<String> = emptyList()

    /** Ex.: "hp=4,atk=252,spe=252" (aplicado quando a faixa usa EVs). */
    @SerializedName("evs")
    var evs: String = ""

    /** Propriedades extras do Cobblemon, ex.: "aspect=alolan" ou "tera_type=fire". */
    @SerializedName("extra")
    var extra: String = ""

    /** Peso no sorteio (maior = aparece mais). */
    @SerializedName("peso")
    var weight: Int = 1

    @SerializedName("papel")
    var role: String = ""
}
