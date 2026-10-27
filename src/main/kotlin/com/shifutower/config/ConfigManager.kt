package com.shifutower.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.shifutower.ShifuTower
import com.shifutower.util.SafeFiles
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/** Lê/grava os arquivos de config/shifutower. */
object ConfigManager {
    val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private val dir: Path get() = FabricLoader.getInstance().configDir.resolve(ShifuTower.MOD_ID)

    var tower: TowerConfig = TowerConfig()
        private set
    var trainers: TrainersConfig = TrainersConfig()
        private set
    var shop: ShopConfig = ShopConfig()
        private set
    var pools: Map<String, List<PoolEntry>> = emptyMap()
        private set

    fun load() {
        Files.createDirectories(dir)
        tower = read("torre.json", TowerConfig::class.java) { TowerConfig() }
        trainers = read("treinadores.json", TrainersConfig::class.java) { TrainersConfig() }
        shop = read("loja.json", ShopConfig::class.java) { ShopConfig() }
        pools = readPools()
        sanitize()
        ShifuTower.LOGGER.info(
            "Config carregada: {} andares, {} faixas, {} treinadores, {} itens na loja, {} grupos de times",
            tower.floors, tower.tiers.size, trainers.trainers.size, shop.entries.size, pools.size,
        )
    }

    fun saveShop() = write("loja.json", shop)

    private fun sanitize() {
        tower.floors = tower.floors.coerceIn(1, MAX_FLOORS)
        tower.attemptsPerDay = tower.attemptsPerDay.coerceAtLeast(1)
        tower.rankingSize = tower.rankingSize.coerceIn(3, 50)
        if (tower.tiers.isEmpty()) tower.tiers = TowerConfig.defaultTiers()
        if (trainers.trainers.isEmpty()) trainers.trainers = TrainersConfig.defaultTrainers()
        var changed = false
        shop.entries.forEachIndexed { index, entry ->
            if (entry.id.isBlank()) {
                entry.id = "item_${index + 1}_${System.nanoTime().toString(36).takeLast(4)}"
                changed = true
            }
        }
        if (changed) saveShop()
    }

    private fun <T : Any> read(name: String, type: Class<T>, default: () -> T): T {
        val file = dir.resolve(name)
        if (Files.notExists(file)) {
            val value = default()
            write(name, value)
            return value
        }
        return try {
            Files.newBufferedReader(file).use { GSON.fromJson(it, type) } ?: default()
        } catch (e: Exception) {
            ShifuTower.LOGGER.error("Erro lendo config/shifutower/{} — usando padrão. Corrija o JSON! ({})", name, e.message)
            default()
        }
    }

    private fun write(name: String, value: Any) {
        try {
            SafeFiles.writeAtomic(dir.resolve(name), GSON.toJson(value))
        } catch (e: Exception) {
            ShifuTower.LOGGER.error("Não foi possível salvar config/shifutower/{}", name, e)
        }
    }

    private fun readPools(): Map<String, List<PoolEntry>> {
        val file = dir.resolve("times.json")
        if (Files.notExists(file)) {
            ConfigManager::class.java.getResourceAsStream("/shifutower/defaults/times.json")?.use { input ->
                Files.copy(input, file)
            }
        }
        val type = object : TypeToken<Map<String, List<PoolEntry>>>() {}.type
        return try {
            Files.newBufferedReader(file).use { GSON.fromJson<Map<String, List<PoolEntry>>>(it, type) } ?: emptyMap()
        } catch (e: Exception) {
            ShifuTower.LOGGER.error("Erro lendo config/shifutower/times.json ({})", e.message)
            emptyMap()
        }
    }

    const val MAX_FLOORS = 40
}
