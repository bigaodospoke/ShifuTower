package com.shifutower.network

import com.shifutower.config.ConfigManager
import com.shifutower.shop.ShopService
import com.shifutower.tower.DailyLimit
import com.shifutower.tower.Ranking
import com.shifutower.tower.TeamGenerator
import com.shifutower.tower.TowerManager
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

object ServerNetworking {
    fun register() {
        PayloadTypeRegistry.playS2C().register(OpenTowerPayload.TYPE, OpenTowerPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RunHudPayload.TYPE, RunHudPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SpeechPayload.TYPE, SpeechPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RunResultPayload.TYPE, RunResultPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(TowerActionPayload.TYPE, TowerActionPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(TowerActionPayload.TYPE) { payload, context ->
            handleAction(context.player(), payload)
        }
    }

    fun send(player: ServerPlayer, payload: CustomPacketPayload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload)
    }

    fun openTower(player: ServerPlayer, tab: Int) {
        send(player, OpenTowerPayload(snapshot(player), tab))
    }

    private fun handleAction(player: ServerPlayer, payload: TowerActionPayload) {
        when (payload.action) {
            TowerActionPayload.START -> {
                val error = TowerManager.start(player)
                if (error != null) {
                    player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED))
                    openTower(player, 0)
                }
            }
            TowerActionPayload.FORFEIT -> TowerManager.forfeit(player)
            TowerActionPayload.BUY -> {
                ShopService.buy(player, payload.arg, payload.arg2)
                openTower(player, 2)
            }
            TowerActionPayload.REFRESH -> openTower(player, payload.arg.coerceIn(0, 2))
        }
    }

    fun snapshot(player: ServerPlayer): TowerSnapshot {
        val record = TowerManager.recordOf(player)
        val config = ConfigManager.tower
        val ranking = Ranking.top(config.rankingSize).map { RankView(it.name, it.timeMs, it.date, it.uuid == player.uuid) }
        val tiers = config.tiers
            .filter { it.chefe || it.fromFloor < config.floors }
            .map {
                val from = if (it.chefe) config.floors else it.fromFloor
                val to = if (it.chefe) config.floors else it.toFloor.coerceAtMost(config.floors - 1)
                TierView(it.name, from, to, it.teamSize, TeamGenerator.levelFor(to, it), it.chefe)
            }
        val boss = ConfigManager.trainers.trainers.firstOrNull { it.boss }
        return TowerSnapshot(
            bp = record.bp,
            attemptsLeft = DailyLimit.attemptsLeft(record),
            attemptsPerDay = config.attemptsPerDay,
            msUntilReset = DailyLimit.millisUntilReset(),
            inRun = TowerManager.runOf(player.uuid) != null,
            floors = config.floors,
            bestFloor = record.bestFloor,
            bestTimeMs = record.bestTimeMs,
            clears = record.clears,
            runs = record.runs,
            rank = Ranking.positionOf(player.uuid),
            maxBp = TowerManager.maxBpPerRun(),
            bossName = boss?.let { "${it.title} ${it.names.firstOrNull().orEmpty()}".trim() } ?: "",
            ranking = ranking,
            shop = ShopService.views(player, record),
            tiers = tiers,
            floorBp = (1..config.floors).map { TowerManager.bpForFloor(it) },
        )
    }
}
