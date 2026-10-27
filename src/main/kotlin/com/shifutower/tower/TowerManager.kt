package com.shifutower.tower

import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.config.TrainerArchetype
import com.shifutower.data.PlayerRecord
import com.shifutower.data.SavedPos
import com.shifutower.data.TowerStorage
import com.shifutower.network.RunHudPayload
import com.shifutower.network.RunResultPayload
import com.shifutower.network.ServerNetworking
import com.shifutower.network.SpeechPayload
import com.shifutower.registry.ModComponents
import com.shifutower.registry.ModItems
import com.shifutower.trophy.TrophyInfo
import com.shifutower.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID
import kotlin.random.Random

/** Controla todos os desafios em andamento. Tudo roda na thread do servidor. */
object TowerManager {
    private val runs = LinkedHashMap<UUID, TowerRun>()
    private val joinChecks = mutableListOf<UUID>()

    /** NPCs criados por /shifutower arena (pré-visualização), que não pertencem a nenhum desafio. */
    val previewNpcs = mutableSetOf<UUID>()
    private val previewReturns = HashMap<UUID, SavedPos>()

    val floors: Int get() = ConfigManager.tower.floors

    fun runOf(uuid: UUID): TowerRun? = runs[uuid]

    fun activeRuns(): Collection<TowerRun> = runs.values

    fun towerLevel(server: MinecraftServer): ServerLevel? = server.getLevel(ShifuTower.TOWER_DIMENSION)

    fun bpForFloor(floor: Int): Int {
        val list = ConfigManager.tower.bpPerFloor
        if (list.isEmpty()) return 5 * floor
        return list.getOrNull(floor - 1) ?: (list.last() + 5 * (floor - list.size))
    }

    fun maxBpPerRun(): Int = (1..floors).sumOf { bpForFloor(it) } + ConfigManager.tower.completionBonus

    // ------------------------------------------------------------------ início

    /** Tenta começar o desafio. Retorna a mensagem de erro, ou null se começou. */
    fun start(player: ServerPlayer): Component? {
        val server = player.server
        val level = towerLevel(server)
            ?: return Component.translatable("shifutower.erro.dimensao")
        if (runs.containsKey(player.uuid)) return Component.translatable("shifutower.erro.ja_na_torre")
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return Component.translatable("shifutower.erro.em_batalha")

        val record = TowerStorage.get(player.uuid)
        record.name = player.gameProfile.name
        if (DailyLimit.attemptsLeft(record) <= 0) {
            return Component.translatable("shifutower.erro.sem_tentativas", TimeFormat.countdown(DailyLimit.millisUntilReset()))
        }
        validateParty(player)?.let { return it }

        DailyLimit.consume(record)
        record.runs++
        val returnPos = SavedPos(
            player.level().dimension().location().toString(),
            player.x, player.y, player.z, player.yRot, player.xRot,
        )
        record.pendingReturn = returnPos
        TowerStorage.markDirty()

        val run = TowerRun(player.uuid, player.gameProfile.name, freeSlot(), System.currentTimeMillis(), returnPos)
        runs[player.uuid] = run
        player.closeContainer()
        player.sendSystemMessage(
            Component.translatable("shifutower.msg.inicio", floors).withStyle(ChatFormatting.GOLD),
        )
        if (!enterFloor(player, level, run)) {
            endRun(run, won = false, reason = "erro", refund = true)
        }
        return null
    }

    private fun validateParty(player: ServerPlayer): Component? {
        val party = player.party()
        val pokemon = party.toList()
        if (pokemon.isEmpty()) return Component.translatable("shifutower.erro.sem_pokemon")
        val banned = ConfigManager.tower.bannedSpecies.map { it.lowercase() }.toSet()
        pokemon.firstOrNull { it.species.name.lowercase() in banned || it.species.resourceIdentifier.path in banned }?.let {
            return Component.translatable("shifutower.erro.proibido", it.getDisplayName(false))
        }
        val maxLevel = ConfigManager.tower.maxPlayerLevel
        if (maxLevel > 0) {
            pokemon.firstOrNull { it.level > maxLevel }?.let {
                return Component.translatable("shifutower.erro.nivel", it.getDisplayName(false), maxLevel)
            }
        }
        return null
    }

    private fun freeSlot(): Int {
        val used = runs.values.map { it.slot }.toSet()
        var slot = 0
        while (slot in used) slot++
        return slot
    }

    // ------------------------------------------------------------------ andares

    private fun enterFloor(player: ServerPlayer, level: ServerLevel, run: TowerRun): Boolean {
        val boss = run.floor >= floors
        val tier = TeamGenerator.tierFor(run.floor)
        removeNpc(player.server, run)
        ArenaBuilder.build(level, run.slot, run.floor, floors, boss)

        val spot = ArenaBuilder.playerSpot(run.slot, run.floor)
        player.teleportTo(level, spot.x, spot.y, spot.z, ArenaBuilder.PLAYER_YAW, 0f)
        player.fallDistance = 0f

        val team = TeamGenerator.generate(run.floor)
        if (team.isEmpty()) {
            player.sendSystemMessage(Component.translatable("shifutower.erro.time_vazio", tier.pool).withStyle(ChatFormatting.RED))
            return false
        }
        val archetype = pickArchetype(run.floor, boss)
        val name = "${archetype.title} ${archetype.names.randomOrNull() ?: "?"}".trim()
        val npc = TrainerSpawner.spawnTrainer(
            level, ArenaBuilder.trainerSpot(run.slot, run.floor), ArenaBuilder.TRAINER_YAW,
            archetype.skin, name, team, TeamGenerator.levelFor(run.floor, tier), tier.aiSkill, boss,
        )
        run.npcId = npc.uuid
        run.npcEntityId = npc.id
        run.archetype = archetype
        run.trainerName = name
        run.tierName = tier.name
        run.boss = boss
        run.phase = Phase.ARRIVING
        run.timer = 30

        val title = if (boss) {
            Component.translatable("shifutower.titulo.chefe").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        } else {
            Component.translatable("shifutower.titulo.andar", run.floor).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        }
        val subtitle = if (boss) {
            Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE)
        } else {
            Component.translatable("shifutower.titulo.faixa", tier.name, run.floor, floors).withStyle(ChatFormatting.GRAY)
        }
        title(player, title, subtitle, 10, 50, 15)
        sound(player, if (boss) SoundEvents.WITHER_SPAWN else SoundEvents.ENDERMAN_TELEPORT, if (boss) 0.4f else 0.8f, 1.0f)
        level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1, spot.z, 60, 0.4, 0.8, 0.4, 0.2)
        sendHud(player, run)
        return true
    }

    fun pickArchetype(floor: Int, boss: Boolean): TrainerArchetype {
        val all = ConfigManager.trainers.trainers
        if (boss) return all.firstOrNull { it.boss } ?: all.first()
        return all.filter { !it.boss && floor in it.minFloor..it.maxFloor }.randomOrNull()
            ?: all.filter { !it.boss }.randomOrNull()
            ?: all.first()
    }

    // ------------------------------------------------------------------ tick

    fun tick(server: MinecraftServer) {
        if (joinChecks.isNotEmpty()) {
            val pending = joinChecks.toList()
            joinChecks.clear()
            pending.forEach { uuid -> server.playerList.getPlayer(uuid)?.let(::checkStranded) }
        }
        if (runs.isEmpty()) return
        val level = towerLevel(server) ?: return
        for (run in runs.values.toList()) {
            val player = server.playerList.getPlayer(run.playerId)
            if (player == null) {
                endRun(run, won = false, reason = "desconectou", teleport = false)
                continue
            }
            if (player.level().dimension() != ShifuTower.TOWER_DIMENSION) {
                player.sendSystemMessage(Component.translatable("shifutower.msg.saiu").withStyle(ChatFormatting.RED))
                endRun(run, won = false, reason = "saiu", teleport = false)
                continue
            }
            keepInsideArena(player, run)
            tickRun(player, level, run)
        }
    }

    private fun keepInsideArena(player: ServerPlayer, run: TowerRun) {
        val spot = ArenaBuilder.playerSpot(run.slot, run.floor)
        val tooLow = player.y < spot.y - 8
        val tooFar = player.position().distanceToSqr(spot.x, player.y, spot.z) > 30.0 * 30.0
        if (tooLow || tooFar) {
            player.teleportTo(player.serverLevel(), spot.x, spot.y, spot.z, ArenaBuilder.PLAYER_YAW, 0f)
            player.fallDistance = 0f
        }
    }

    private fun tickRun(player: ServerPlayer, level: ServerLevel, run: TowerRun) {
        run.timer--
        when (run.phase) {
            Phase.ARRIVING -> if (run.timer <= 0) {
                speak(player, level, run, run.archetype?.intro.orEmpty(), (ConfigManager.tower.secondsBeforeBattle * 20).coerceAtLeast(40))
                run.phase = Phase.INTRO
                run.timer = (ConfigManager.tower.secondsBeforeBattle * 20).coerceAtLeast(20)
            }

            Phase.INTRO -> if (run.timer <= 0) startBattle(player, level, run)

            Phase.BATTLE -> {
                val id = run.battleId
                if (id == null || BattleRegistry.getBattle(id) == null) {
                    // Batalha sumiu sem evento de vitória (ex.: /stopbattle). Espera um pouco e conta como derrota.
                    if (++run.battleWatchdog > 60) onBattleEnded(run, won = false)
                } else {
                    run.battleWatchdog = 0
                }
            }

            Phase.AFTER_BATTLE -> if (run.timer <= 0) {
                when {
                    !run.wonLast -> endRun(run, won = false, reason = "derrota")
                    run.floor >= floors -> celebrate(player, level, run)
                    else -> {
                        run.floor++
                        if (!enterFloor(player, level, run)) endRun(run, won = false, reason = "erro")
                    }
                }
            }

            Phase.CELEBRATION -> {
                if (run.timer % 10 == 0) {
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.x, player.y + 1.2, player.z, 40, 0.6, 0.8, 0.6, 0.4)
                    level.sendParticles(ParticleTypes.FIREWORK, player.x, player.y + 3.0, player.z, 25, 1.5, 0.5, 1.5, 0.1)
                }
                if (run.timer <= 0) endRun(run, won = true, reason = "vitoria")
            }
        }
    }

    // ------------------------------------------------------------------ batalha

    private fun startBattle(player: ServerPlayer, level: ServerLevel, run: TowerRun) {
        val npc = run.npcId?.let { level.getEntity(it) } as? NPCEntity
        if (npc == null) {
            ShifuTower.LOGGER.warn("Treinador do andar {} de {} sumiu; recriando o andar", run.floor, run.playerName)
            if (!enterFloor(player, level, run)) endRun(run, won = false, reason = "erro", refund = run.floor == 1)
            return
        }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            player.displayClientMessage(Component.translatable("shifutower.erro.em_batalha").withStyle(ChatFormatting.RED), true)
            run.timer = 40
            return
        }
        val forced = ConfigManager.tower.forcedBattleLevel
        val format = if (forced > 0) BattleFormat.GEN_9_SINGLES.copy(adjustLevel = forced.coerceIn(1, 100)) else BattleFormat.GEN_9_SINGLES
        // cloneParties = true: o time real do jogador não perde HP/PP nem tem o nível alterado.
        BattleBuilder.pvn(player, npc, null, format, true, true, player.party())
            .ifSuccessful { battle ->
                run.battleId = battle.battleId
                run.battleWatchdog = 0
                run.phase = Phase.BATTLE
            }
            .ifErrored { error ->
                error.sendTo(player) { it.withStyle(ChatFormatting.RED) }
                endRun(run, won = false, reason = "erro", refund = run.floor == 1)
            }
    }

    fun onBattleFinished(battleId: UUID, winners: Iterable<UUID>) {
        val run = runs.values.firstOrNull { it.battleId == battleId } ?: return
        onBattleEnded(run, won = run.playerId in winners)
    }

    fun onPlayerFled(battleId: UUID) {
        val run = runs.values.firstOrNull { it.battleId == battleId } ?: return
        onBattleEnded(run, won = false)
    }

    private fun onBattleEnded(run: TowerRun, won: Boolean) {
        if (run.phase != Phase.BATTLE) return
        val server = ShifuTower.server ?: return
        val player = server.playerList.getPlayer(run.playerId) ?: return
        val level = towerLevel(server) ?: return
        run.battleId = null
        run.wonLast = won
        run.phase = Phase.AFTER_BATTLE
        run.timer = (ConfigManager.tower.secondsAfterBattle * 20).coerceAtLeast(40)

        val lines = if (won) run.archetype?.npcLoses.orEmpty() else run.archetype?.npcWins.orEmpty()
        speak(player, level, run, lines, run.timer)

        if (won) {
            val record = TowerStorage.get(run.playerId)
            val bp = bpForFloor(run.floor)
            record.bp += bp
            record.totalBpEarned += bp
            record.bestFloor = maxOf(record.bestFloor, run.floor)
            run.bpEarned += bp
            TowerStorage.markDirty()
            player.displayClientMessage(
                Component.translatable("shifutower.msg.andar_vencido", run.floor, bp).withStyle(ChatFormatting.GREEN),
                true,
            )
            sound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f)
            sendHud(player, run)
        } else {
            sound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f)
        }
    }

    // ------------------------------------------------------------------ vitória final

    private fun celebrate(player: ServerPlayer, level: ServerLevel, run: TowerRun) {
        val record = TowerStorage.get(run.playerId)
        val timeMs = System.currentTimeMillis() - run.startedAt
        run.finalTimeMs = timeMs
        val bonus = ConfigManager.tower.completionBonus
        record.bp += bonus
        record.totalBpEarned += bonus
        run.bpEarned += bonus
        record.clears++
        if (record.bestTimeMs < 0 || timeMs < record.bestTimeMs) {
            record.bestTimeMs = timeMs
            record.bestTimeDate = DailyLimit.todayDisplay()
            record.bestTimeAt = System.currentTimeMillis()
            run.newRecord = true
        }
        TowerStorage.markDirty()
        val rank = Ranking.positionOf(run.playerId)

        if (ConfigManager.tower.trophyEveryClear || record.clears == 1) {
            val trophy = ItemStack(ModItems.TROPHY)
            trophy.set(ModComponents.TROPHY_INFO, TrophyInfo(run.playerName, timeMs, DailyLimit.todayDisplay(), rank, floors))
            player.inventory.placeItemBackInInventory(trophy)
            run.trophyGiven = true
        }

        title(
            player,
            Component.translatable("shifutower.titulo.vitoria").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.translatable("shifutower.titulo.vitoria_sub", TimeFormat.duration(timeMs)).withStyle(ChatFormatting.YELLOW),
            10, 80, 20,
        )
        sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        sound(player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f)
        if (ConfigManager.tower.broadcastClear) {
            val msg = Component.translatable(
                "shifutower.msg.anuncio",
                Component.literal(run.playerName).withStyle(ChatFormatting.YELLOW),
                Component.literal(TimeFormat.duration(timeMs)).withStyle(ChatFormatting.AQUA),
                Component.literal("#$rank").withStyle(ChatFormatting.GOLD),
            ).withStyle(ChatFormatting.GOLD)
            player.server.playerList.broadcastSystemMessage(msg, false)
        }
        run.phase = Phase.CELEBRATION
        run.timer = 100
        sendHud(player, run)
    }

    // ------------------------------------------------------------------ fim

    /** Desistência pela GUI ou comando. */
    fun forfeit(player: ServerPlayer): Boolean {
        val run = runs[player.uuid] ?: return false
        player.sendSystemMessage(Component.translatable("shifutower.msg.desistiu").withStyle(ChatFormatting.RED))
        endRun(run, won = false, reason = "desistiu")
        return true
    }

    fun endRun(run: TowerRun, won: Boolean, reason: String, teleport: Boolean = true, refund: Boolean = false) {
        if (runs[run.playerId] !== run) return
        runs.remove(run.playerId)
        val server = ShifuTower.server ?: return

        run.battleId?.let { id -> BattleRegistry.getBattle(id)?.takeIf { !it.ended }?.end() }
        run.battleId = null
        removeNpc(server, run)

        val record = TowerStorage.get(run.playerId)
        if (refund) DailyLimit.refund(record)
        val player = server.playerList.getPlayer(run.playerId)
        if (player != null) {
            if (teleport && player.level().dimension() == ShifuTower.TOWER_DIMENSION) {
                returnPlayer(player, record.pendingReturn ?: run.returnPos)
            }
            if (teleport || player.level().dimension() != ShifuTower.TOWER_DIMENSION) record.pendingReturn = null
            ServerNetworking.send(player, RunHudPayload.INACTIVE)
            val floorReached = if (won) floors else (run.floor - 1).coerceAtLeast(0)
            ServerNetworking.send(
                player,
                RunResultPayload(
                    won, floorReached, floors,
                    if (won) run.finalTimeMs else System.currentTimeMillis() - run.startedAt,
                    run.bpEarned, record.bp, if (won) Ranking.positionOf(run.playerId) else -1,
                    run.newRecord, run.trophyGiven, reason,
                ),
            )
            if (!won && reason != "erro") {
                player.sendSystemMessage(
                    Component.translatable("shifutower.msg.fim", floorReached, floors, run.bpEarned).withStyle(ChatFormatting.GRAY),
                )
            }
        }
        TowerStorage.markDirty()
    }

    private fun removeNpc(server: MinecraftServer, run: TowerRun) {
        val id = run.npcId ?: return
        towerLevel(server)?.getEntity(id)?.discard()
        run.npcId = null
        run.npcEntityId = -1
    }

    private fun returnPlayer(player: ServerPlayer, pos: SavedPos) {
        val server = player.server
        val key = ResourceLocation.tryParse(pos.dimension)?.let { ResourceKey.create(Registries.DIMENSION, it) }
        val target = key?.let { server.getLevel(it) }
        if (target == null || key == ShifuTower.TOWER_DIMENSION) {
            val overworld = server.overworld()
            val spawn = overworld.sharedSpawnPos
            player.teleportTo(overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, 0f, 0f)
        } else {
            player.teleportTo(target, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
        }
        player.fallDistance = 0f
    }

    // ------------------------------------------------------------------ conexão

    fun onJoin(player: ServerPlayer) {
        joinChecks += player.uuid
    }

    fun onDisconnect(player: ServerPlayer) {
        val run = runs[player.uuid] ?: return
        endRun(run, won = false, reason = "desconectou", teleport = false)
    }

    /** Jogador entrou no servidor ainda dentro da torre (caiu/desconectou no meio): devolve para onde estava. */
    private fun checkStranded(player: ServerPlayer) {
        if (runs.containsKey(player.uuid)) return
        val record = TowerStorage.find(player.uuid)
        if (player.level().dimension() == ShifuTower.TOWER_DIMENSION) {
            returnPlayer(player, record?.pendingReturn ?: SavedPos(Level.OVERWORLD.location().toString()))
            player.sendSystemMessage(Component.translatable("shifutower.msg.devolvido").withStyle(ChatFormatting.GRAY))
        }
        if (record?.pendingReturn != null) {
            record.pendingReturn = null
            TowerStorage.markDirty()
        }
    }

    fun shutdown(server: MinecraftServer) {
        for (run in runs.values.toList()) {
            server.playerList.getPlayer(run.playerId)?.sendSystemMessage(
                Component.translatable("shifutower.msg.reinicio").withStyle(ChatFormatting.YELLOW),
            )
            endRun(run, won = false, reason = "reinicio", refund = true)
        }
        runs.clear()
    }

    // ------------------------------------------------------------------ utilidades

    private fun speak(player: ServerPlayer, level: ServerLevel, run: TowerRun, lines: List<String>, ticks: Int) {
        val line = lines.randomOrNull(Random.Default) ?: return
        val text = format(line, run.playerName, run.floor)
        val style = if (run.boss) SpeechPayload.STYLE_BOSS else SpeechPayload.STYLE_TRAINER
        ServerNetworking.send(player, SpeechPayload(run.npcEntityId, Component.literal(run.trainerName), Component.literal(text), ticks, style))
        player.sendSystemMessage(
            Component.empty()
                .append(Component.literal(run.trainerName).withStyle(if (run.boss) ChatFormatting.GOLD else ChatFormatting.YELLOW))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
        )
    }

    fun format(line: String, playerName: String, floor: Int): String =
        line.replace("{jogador}", playerName)
            .replace("{andar}", floor.toString())
            .replace("{total}", floors.toString())

    private fun sendHud(player: ServerPlayer, run: TowerRun) {
        ServerNetworking.send(
            player,
            RunHudPayload(
                true, run.floor, floors, System.currentTimeMillis() - run.startedAt,
                run.bpEarned, run.boss, run.trainerName, run.tierName,
            ),
        )
    }

    private fun title(player: ServerPlayer, title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
        player.connection.send(ClientboundSetSubtitleTextPacket(subtitle))
        player.connection.send(ClientboundSetTitleTextPacket(title))
    }

    private fun sound(player: ServerPlayer, sound: SoundEvent, volume: Float, pitch: Float) {
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch)
    }

    fun recordOf(player: ServerPlayer): PlayerRecord = TowerStorage.get(player.uuid).also { it.name = player.gameProfile.name }

    // ------------------------------------------------------------------ pré-visualização (admin)

    /** Monta a arena de um andar e coloca um treinador com time sorteado, sem batalha. */
    fun preview(server: MinecraftServer, floor: Int, viewer: ServerPlayer?): NPCEntity? {
        val level = towerLevel(server) ?: return null
        val slot = PREVIEW_SLOT
        val boss = floor >= floors
        val tier = TeamGenerator.tierFor(floor)
        previewNpcs.mapNotNull { level.getEntity(it) }.forEach { it.discard() }
        previewNpcs.clear()
        ArenaBuilder.build(level, slot, floor, floors, boss)
        val archetype = pickArchetype(floor, boss)
        val npc = TrainerSpawner.spawnTrainer(
            level, ArenaBuilder.trainerSpot(slot, floor), ArenaBuilder.TRAINER_YAW, archetype.skin,
            "${archetype.title} ${archetype.names.randomOrNull() ?: "?"}".trim(),
            TeamGenerator.generate(floor), TeamGenerator.levelFor(floor, tier), tier.aiSkill, boss,
        )
        previewNpcs += npc.uuid
        if (viewer != null && runs[viewer.uuid] == null) {
            if (viewer.level().dimension() != ShifuTower.TOWER_DIMENSION) {
                previewReturns[viewer.uuid] = SavedPos(
                    viewer.level().dimension().location().toString(), viewer.x, viewer.y, viewer.z, viewer.yRot, viewer.xRot,
                )
            }
            val spot = ArenaBuilder.playerSpot(slot, floor)
            viewer.teleportTo(level, spot.x, spot.y, spot.z, ArenaBuilder.PLAYER_YAW, 0f)
        }
        return npc
    }

    /** Sai da pré-visualização. */
    fun leavePreview(player: ServerPlayer): Boolean {
        if (player.level().dimension() != ShifuTower.TOWER_DIMENSION || runs.containsKey(player.uuid)) return false
        returnPlayer(player, previewReturns.remove(player.uuid) ?: SavedPos(Level.OVERWORLD.location().toString()))
        return true
    }

    private const val PREVIEW_SLOT = 1000
}
