package com.shifutower.tower

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.network.ServerNetworking
import com.shifutower.network.SpeechPayload
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.UUID

/** Liga a torre aos eventos do Cobblemon e do Fabric. */
object TowerEvents {
    private val lastInteract = HashMap<UUID, Long>()
    private val loadedTrainers = mutableListOf<Entity>()

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            TowerManager.onBattleFinished(event.battle.battleId, event.winners.flatMap { it.getPlayerUUIDs() })
        }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            TowerManager.onPlayerFled(event.battle.battleId)
        }
        // Nada de Pokémon selvagem aparecendo no meio da arena
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.HIGHEST) { event ->
            if (event.spawnablePosition.world.dimension() == ShifuTower.TOWER_DIMENSION) event.cancel()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> TowerManager.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> TowerManager.onDisconnect(handler.player) }

        UseEntityCallback.EVENT.register { player, level, hand, entity, hitResult ->
            onUseEntity(player, level, hand, entity, hitResult == null)
        }
        AttackEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (!level.isClientSide && (TrainerSpawner.isTrainer(entity) || TrainerSpawner.isReceptionist(entity))) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        // Ninguém quebra ou constrói nas arenas (exceto criativo)
        PlayerBlockBreakEvents.BEFORE.register { level, player, _, _, _ -> !inTowerAsSurvival(level, player) }
        UseBlockCallback.EVENT.register { player, level, _, _ ->
            if (inTowerAsSurvival(level, player)) InteractionResult.FAIL else InteractionResult.PASS
        }

        // NPCs da torre que sobraram de um crash são removidos quando o chunk carrega
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (TrainerSpawner.isTrainer(entity)) loadedTrainers += entity
        }
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            if (loadedTrainers.isEmpty()) return@register
            val owned = TowerManager.activeRuns().mapNotNull { it.npcId }.toSet() + TowerManager.previewNpcs
            loadedTrainers.filter { !it.isRemoved && it.uuid !in owned }.forEach { it.discard() }
            loadedTrainers.clear()
        }
    }

    private fun inTowerAsSurvival(level: Level, player: Player): Boolean =
        level.dimension() == ShifuTower.TOWER_DIMENSION && !player.isCreative && !player.isSpectator

    private fun onUseEntity(player: Player, level: Level, hand: InteractionHand, entity: Entity, plainInteract: Boolean): InteractionResult {
        if (level.isClientSide || player !is ServerPlayer) return InteractionResult.PASS
        val receptionist = TrainerSpawner.isReceptionist(entity)
        if (!receptionist && !TrainerSpawner.isTrainer(entity)) return InteractionResult.PASS
        if (!receptionist || hand != InteractionHand.MAIN_HAND || !plainInteract) return InteractionResult.SUCCESS

        val now = level.gameTime
        if (now - (lastInteract[player.uuid] ?: Long.MIN_VALUE / 2) < 10) return InteractionResult.SUCCESS
        lastInteract[player.uuid] = now

        val record = TowerManager.recordOf(player)
        val lines = if (DailyLimit.attemptsLeft(record) > 0) {
            ConfigManager.trainers.receptionist.welcome
        } else {
            ConfigManager.trainers.receptionist.noAttempts
        }
        lines.randomOrNull()?.let { line ->
            val text = TowerManager.format(line, player.gameProfile.name, 0)
            ServerNetworking.send(
                player,
                SpeechPayload(
                    entity.id, entity.displayName ?: Component.literal(ConfigManager.trainers.receptionist.name),
                    Component.literal(text), 80, SpeechPayload.STYLE_RECEPTIONIST,
                ),
            )
        }
        ServerNetworking.openTower(player, 0)
        return InteractionResult.SUCCESS
    }
}
