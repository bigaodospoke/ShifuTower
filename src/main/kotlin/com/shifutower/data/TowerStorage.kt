package com.shifutower.data

import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.util.SafeFiles
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Posição salva para devolver o jogador ao sair da torre. */
class SavedPos(
    @SerializedName("dimensao") var dimension: String = "minecraft:overworld",
    @SerializedName("x") var x: Double = 0.0,
    @SerializedName("y") var y: Double = 0.0,
    @SerializedName("z") var z: Double = 0.0,
    @SerializedName("yaw") var yaw: Float = 0f,
    @SerializedName("pitch") var pitch: Float = 0f,
)

class PlayerRecord {
    @SerializedName("nome") var name: String = ""
    @SerializedName("bp") var bp: Int = 0
    @SerializedName("bpTotalGanho") var totalBpEarned: Int = 0
    @SerializedName("diaTentativa") var attemptDay: String = ""
    @SerializedName("tentativasUsadas") var attemptsUsed: Int = 0
    @SerializedName("melhorTempoMs") var bestTimeMs: Long = -1
    @SerializedName("dataMelhorTempo") var bestTimeDate: String = ""
    @SerializedName("melhorTempoEm") var bestTimeAt: Long = 0
    @SerializedName("vitorias") var clears: Int = 0
    @SerializedName("melhorAndar") var bestFloor: Int = 0
    @SerializedName("desafios") var runs: Int = 0
    @SerializedName("compras") var purchases: MutableMap<String, Int> = mutableMapOf()
    /** Preenchido enquanto o jogador está na torre; usado para devolvê-lo se cair/desconectar. */
    @SerializedName("retornoPendente") var pendingReturn: SavedPos? = null
}

/** Dados persistentes dos jogadores em <mundo>/shifutower/jogadores.json */
object TowerStorage {
    private var file: Path? = null
    private var records: MutableMap<String, PlayerRecord> = mutableMapOf()
    private var dirty = false
    private var ticksSinceSave = 0

    fun load(server: MinecraftServer) {
        val path = server.getWorldPath(LevelResource.ROOT).resolve(ShifuTower.MOD_ID).resolve("jogadores.json").normalize()
        file = path
        records = mutableMapOf()
        if (Files.exists(path)) {
            try {
                val type = object : TypeToken<MutableMap<String, PlayerRecord>>() {}.type
                records = Files.newBufferedReader(path).use {
                    ConfigManager.GSON.fromJson<MutableMap<String, PlayerRecord>>(it, type)
                } ?: mutableMapOf()
            } catch (e: Exception) {
                ShifuTower.LOGGER.error("Falha ao ler {} — fazendo backup e começando do zero", path, e)
                runCatching { Files.copy(path, path.resolveSibling("jogadores.corrompido.${System.currentTimeMillis()}.json")) }
            }
        }
        ShifuTower.LOGGER.info("ShifuTower: {} jogadores carregados", records.size)
    }

    fun get(uuid: UUID): PlayerRecord = records.getOrPut(uuid.toString()) { PlayerRecord() }

    fun find(uuid: UUID): PlayerRecord? = records[uuid.toString()]

    fun all(): Map<String, PlayerRecord> = records

    fun markDirty() {
        dirty = true
    }

    fun tick() {
        if (!dirty) return
        if (++ticksSinceSave >= 100) save()
    }

    fun save() {
        val path = file ?: return
        ticksSinceSave = 0
        dirty = false
        try {
            SafeFiles.writeAtomic(path, ConfigManager.GSON.toJson(records))
        } catch (e: Exception) {
            dirty = true
            ShifuTower.LOGGER.error("Falha ao salvar {}", path, e)
        }
    }
}
