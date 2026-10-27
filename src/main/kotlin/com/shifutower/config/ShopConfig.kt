package com.shifutower.config

import com.google.gson.annotations.SerializedName

/** config/shifutower/loja.json — itens que podem ser comprados com Battle Points. */
class ShopConfig {
    @SerializedName("itens")
    var entries: MutableList<ShopEntry> = defaultEntries()

    companion object {
        fun defaultEntries(): MutableList<ShopEntry> = mutableListOf(
            ShopEntry("bala_rara", "cobblemon:rare_candy", 3, 30),
            ShopEntry("doce_exp_xl", "cobblemon:exp_candy_xl", 5, 25),
            ShopEntry("pp_max", "cobblemon:pp_max", 1, 40),
            ShopEntry("capsula_habilidade", "cobblemon:ability_capsule", 1, 60),
            ShopEntry("adesivo_habilidade", "cobblemon:ability_patch", 1, 150),
            ShopEntry("menta_adamant", "cobblemon:adamant_mint", 1, 40),
            ShopEntry("menta_jolly", "cobblemon:jolly_mint", 1, 40),
            ShopEntry("menta_modest", "cobblemon:modest_mint", 1, 40),
            ShopEntry("menta_timid", "cobblemon:timid_mint", 1, 40),
            ShopEntry("peso_poder", "cobblemon:power_weight", 1, 60),
            ShopEntry("bracadeira_poder", "cobblemon:power_bracer", 1, 60),
            ShopEntry("cinto_poder", "cobblemon:power_belt", 1, 60),
            ShopEntry("lente_poder", "cobblemon:power_lens", 1, 60),
            ShopEntry("faixa_poder", "cobblemon:power_band", 1, 60),
            ShopEntry("tornozeleira_poder", "cobblemon:power_anklet", 1, 60),
            ShopEntry("orbe_vida", "cobblemon:life_orb", 1, 90),
            ShopEntry("faixa_escolha", "cobblemon:choice_band", 1, 90),
            ShopEntry("oculos_escolha", "cobblemon:choice_specs", 1, 90),
            ShopEntry("lenco_escolha", "cobblemon:choice_scarf", 1, 90),
            ShopEntry("sobras", "cobblemon:leftovers", 1, 90),
            ShopEntry("colete_assalto", "cobblemon:assault_vest", 1, 90),
            ShopEntry("faixa_foco", "cobblemon:focus_sash", 1, 70),
            ShopEntry("no_destino", "cobblemon:destiny_knot", 1, 120),
            ShopEntry("master_ball", "cobblemon:master_ball", 1, 600, limit = 1, displayName = "&5&lMaster Ball &7(1 por jogador)"),
        )
    }
}

class ShopEntry(
    /** Identificador estável (usado para o limite de compras por jogador). */
    @SerializedName("id") var id: String = "",
    /** Id do item, ex.: "cobblemon:rare_candy". Ignorado se "itemCompleto" estiver preenchido. */
    @SerializedName("item") var item: String = "minecraft:stone",
    @SerializedName("quantidade") var count: Int = 1,
    @SerializedName("preco") var price: Int = 10,
    /** 0 = sem limite. */
    @SerializedName("limitePorJogador") var limit: Int = 0,
    /** Nome mostrado na loja (aceita códigos de cor com &). Vazio = nome do item. */
    @SerializedName("nome") var displayName: String = "",
    @SerializedName("descricao") var description: String = "",
    /** Item com todos os dados (encantamentos, nome, lore...) em SNBT. Preenchido pelo comando /shifutower loja adicionar. */
    @SerializedName("itemCompleto") var fullItem: String = "",
    /** Comandos executados pelo servidor na compra. {jogador} = nome do comprador. */
    @SerializedName("comandos") var commands: List<String> = emptyList(),
    /** Se true, não entrega item (só executa os comandos). */
    @SerializedName("somenteComandos") var commandsOnly: Boolean = false,
)
