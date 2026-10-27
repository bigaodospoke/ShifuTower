package com.shifutower.tower

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.config.PoolEntry
import com.shifutower.config.TierConfig
import kotlin.random.Random

/** Monta os times aleatórios dos treinadores de cada andar. */
object TeamGenerator {
    private val PERMANENT_STATS: List<Stat> = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED,
    )

    private val EV_KEYS: Map<String, Stat> = mapOf(
        "hp" to Stats.HP, "atk" to Stats.ATTACK, "def" to Stats.DEFENCE,
        "spa" to Stats.SPECIAL_ATTACK, "spd" to Stats.SPECIAL_DEFENCE, "spe" to Stats.SPEED,
    )

    fun tierFor(floor: Int): TierConfig {
        val config = ConfigManager.tower
        if (floor >= config.floors) config.tiers.firstOrNull { it.chefe }?.let { return it }
        return config.tiers.firstOrNull { !it.chefe && floor in it.fromFloor..it.toFloor }
            ?: config.tiers.filter { !it.chefe }.minByOrNull { distance(floor, it) }
            ?: config.tiers.first()
    }

    private fun distance(floor: Int, tier: TierConfig): Int = when {
        floor < tier.fromFloor -> tier.fromFloor - floor
        floor > tier.toFloor -> floor - tier.toFloor
        else -> 0
    }

    fun levelFor(floor: Int, tier: TierConfig): Int {
        val span = (tier.toFloor - tier.fromFloor).coerceAtLeast(1)
        val t = ((floor - tier.fromFloor).toDouble() / span).coerceIn(0.0, 1.0)
        return (tier.levelMin + (tier.levelMax - tier.levelMin) * t).toInt().coerceIn(1, 100)
    }

    fun generate(floor: Int, random: Random = Random.Default): List<Pokemon> {
        val tier = tierFor(floor)
        val pool = ConfigManager.pools[tier.pool].orEmpty()
        if (pool.isEmpty()) {
            ShifuTower.LOGGER.error("Grupo '{}' (faixa {}) está vazio ou não existe em times.json", tier.pool, tier.name)
            return emptyList()
        }
        val level = levelFor(floor, tier)
        val team = mutableListOf<Pokemon>()
        for (species in pickSpecies(pool, tier.teamSize.coerceIn(1, 6), random)) {
            val entries = pool.filter { it.species.equals(species, ignoreCase = true) }
            val entry = weighted(entries, random) { it.weight } ?: continue
            build(entry, level, tier, random)?.let(team::add)
        }
        return team
    }

    private fun pickSpecies(pool: List<PoolEntry>, size: Int, random: Random): List<String> {
        val weights = pool.groupBy { it.species.lowercase() }
            .filterKeys { PokemonSpecies.getByName(it) != null }
            .mapValues { (_, entries) -> entries.maxOf { it.weight.coerceAtLeast(1) } }
        val withModel = weights.filterKeys { PokemonSpecies.getByName(it)?.implemented == true }
        val chosen = mutableListOf<String>()
        val first = if (ConfigManager.tower.preferImplemented && withModel.isNotEmpty()) withModel else weights
        drawWithoutReplacement(first, size, random, chosen)
        if (chosen.size < size) drawWithoutReplacement(weights.filterKeys { it !in chosen }, size - chosen.size, random, chosen)
        return chosen
    }

    private fun drawWithoutReplacement(weights: Map<String, Int>, count: Int, random: Random, into: MutableList<String>) {
        val remaining = weights.filterKeys { it !in into }.toMutableMap()
        repeat(count) {
            if (remaining.isEmpty()) return
            val pick = weighted(remaining.keys.toList(), random) { remaining.getValue(it) } ?: return
            into += pick
            remaining.remove(pick)
        }
    }

    private fun <T> weighted(items: List<T>, random: Random, weight: (T) -> Int): T? {
        if (items.isEmpty()) return null
        val total = items.sumOf { weight(it).coerceAtLeast(1) }
        var roll = random.nextInt(total)
        for (item in items) {
            roll -= weight(item).coerceAtLeast(1)
            if (roll < 0) return item
        }
        return items.last()
    }

    fun build(entry: PoolEntry, level: Int, tier: TierConfig, random: Random): Pokemon? {
        val validMoves = entry.moves.map { it.lowercase() }.distinct().filter { Moves.getByName(it) != null }
        val damaging = validMoves.filter { !isStatus(it) }
        var item = entry.items.randomOrNull(random)
        val choice = item != null && item.contains("choice_")
        if (choice && damaging.size < 4) {
            item = entry.items.filterNot { it.contains("choice_") }.randomOrNull(random) ?: "cobblemon:life_orb"
        }
        val moves = pickMoves(validMoves, damaging, choice && damaging.size >= 4, random)

        val parts = mutableListOf(entry.species.lowercase(), "level=$level")
        if (moves.isNotEmpty()) parts += "moves=" + moves.joinToString(",")
        item?.let { parts += "held_item=$it" }
        entry.natures.randomOrNull(random)?.let { parts += "nature=$it" }
        entry.abilities.randomOrNull(random)?.let { parts += "ability=$it" }
        if (tier.shinyChance > 0 && random.nextDouble() < tier.shinyChance) parts += "shiny=yes"
        if (entry.extra.isNotBlank()) parts += entry.extra.trim()

        return try {
            val pokemon = PokemonProperties.parse(parts.joinToString(" ")).create()
            val ivMin = tier.ivMin.coerceIn(0, 31)
            val ivMax = tier.ivMax.coerceIn(ivMin, 31)
            PERMANENT_STATS.forEach { pokemon.setIV(it, random.nextInt(ivMin, ivMax + 1)) }
            if (tier.useEvs) applyEvs(pokemon, entry.evs)
            pokemon.heal()
            pokemon
        } catch (e: Exception) {
            ShifuTower.LOGGER.warn("Não foi possível criar '{}': {}", parts.joinToString(" "), e.message)
            null
        }
    }

    private fun pickMoves(valid: List<String>, damaging: List<String>, onlyDamaging: Boolean, random: Random): List<String> {
        if (onlyDamaging) return damaging.shuffled(random).take(4)
        val result = damaging.shuffled(random).take(2).toMutableList()
        for (move in valid.shuffled(random)) {
            if (result.size >= 4) break
            if (move !in result) result += move
        }
        return result
    }

    private fun isStatus(move: String): Boolean =
        Moves.getByName(move)?.damageCategory == DamageCategories.STATUS

    private fun applyEvs(pokemon: Pokemon, spec: String) {
        if (spec.isBlank()) return
        for (part in spec.split(',')) {
            val (key, value) = part.split('=').map { it.trim() }.takeIf { it.size == 2 } ?: continue
            val stat = EV_KEYS[key.lowercase()] ?: continue
            pokemon.setEV(stat, value.toIntOrNull()?.coerceIn(0, 252) ?: continue)
        }
    }
}
