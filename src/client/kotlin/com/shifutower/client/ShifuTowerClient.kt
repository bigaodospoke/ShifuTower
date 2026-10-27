package com.shifutower.client

import com.shifutower.client.bubble.SpeechBubbles
import com.shifutower.client.hud.RunHud
import com.shifutower.client.render.TrophyRenderer
import com.shifutower.client.screen.ResultScreen
import com.shifutower.client.screen.TowerScreen
import com.shifutower.network.OpenTowerPayload
import com.shifutower.network.RunHudPayload
import com.shifutower.network.RunResultPayload
import com.shifutower.network.SpeechPayload
import com.shifutower.registry.ModBlockEntities
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers

object ShifuTowerClient : ClientModInitializer {
    /** Resultado que chegou enquanto a tela de batalha do Cobblemon ainda estava aberta. */
    private var pendingResult: RunResultPayload? = null
    private var pendingResultDelay = 0

    override fun onInitializeClient() {
        BlockEntityRenderers.register(ModBlockEntities.TROPHY, ::TrophyRenderer)

        ClientPlayNetworking.registerGlobalReceiver(OpenTowerPayload.TYPE) { payload, context ->
            val mc = context.client()
            val current = mc.screen
            if (current is TowerScreen) current.update(payload.snapshot, payload.tab) else mc.setScreen(TowerScreen(payload.snapshot, payload.tab))
        }
        ClientPlayNetworking.registerGlobalReceiver(RunHudPayload.TYPE) { payload, _ -> RunHud.update(payload) }
        ClientPlayNetworking.registerGlobalReceiver(SpeechPayload.TYPE) { payload, _ -> SpeechBubbles.show(payload) }
        ClientPlayNetworking.registerGlobalReceiver(RunResultPayload.TYPE) { payload, _ ->
            pendingResult = payload
            pendingResultDelay = 10
        }

        HudRenderCallback.EVENT.register { graphics, delta -> RunHud.render(graphics, delta.getGameTimeDeltaPartialTick(false)) }
        WorldRenderEvents.AFTER_ENTITIES.register(SpeechBubbles::render)

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            SpeechBubbles.tick(mc)
            tickPendingResult(mc)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            RunHud.update(RunHudPayload.INACTIVE)
            SpeechBubbles.clear()
            pendingResult = null
        }
    }

    private fun tickPendingResult(mc: Minecraft) {
        val result = pendingResult ?: return
        if (--pendingResultDelay > 0) return
        // Espera a tela de batalha do Cobblemon fechar antes de mostrar o resultado
        val screen = mc.screen
        if (screen != null && screen !is TowerScreen && screen.javaClass.name.contains("battle", ignoreCase = true)) {
            pendingResultDelay = 10
            return
        }
        pendingResult = null
        mc.setScreen(ResultScreen(result))
    }
}
