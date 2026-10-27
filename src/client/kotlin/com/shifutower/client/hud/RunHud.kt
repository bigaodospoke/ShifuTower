package com.shifutower.client.hud

import com.shifutower.client.screen.UiKit
import com.shifutower.network.RunHudPayload
import com.shifutower.util.TimeFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.sin

/** Painelzinho no topo da tela durante o desafio: andar, progresso, cronômetro e BP ganhos. */
object RunHud {
    private var state: RunHudPayload = RunHudPayload.INACTIVE
    private var startedAtLocal = 0L

    fun update(payload: RunHudPayload) {
        state = payload
        startedAtLocal = System.currentTimeMillis() - payload.elapsedMs
    }

    val active: Boolean get() = state.active

    fun render(g: GuiGraphics, partialTick: Float) {
        val s = state
        if (!s.active) return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        val font = mc.font

        val w = 168
        val h = 38
        val x = (g.guiWidth() - w) / 2
        val y = 3
        val pulse = ((sin(System.currentTimeMillis() / 250.0) + 1) / 2 * 80).toInt()
        val border = if (s.boss) UiKit.brighten(UiKit.GOLD_DARK, pulse) else UiKit.GOLD_DARK
        UiKit.panel(g, x, y, w, h, UiKit.withAlpha(UiKit.PANEL, 0xE6), border, if (s.boss) UiKit.GOLD_LIGHT else UiKit.GOLD)

        // Linha 1: título + andar
        UiKit.icon(g, if (s.boss) UiKit.ICON_CROWN else UiKit.ICON_TOWER, x + 5, y + 4, 10)
        val title = if (s.boss) Component.translatable("shifutower.hud.chefe") else Component.translatable("shifutower.hud.titulo")
        g.drawString(font, title, x + 17, y + 5, if (s.boss) UiKit.GOLD else UiKit.TEXT, true)
        val floorText = Component.translatable("shifutower.hud.andar", s.floor, s.floors)
        g.drawString(font, floorText, x + w - 6 - font.width(floorText), y + 5, UiKit.GOLD_LIGHT, true)

        // Linha 2: barra de progresso dos andares
        val barX = x + 6
        val barY = y + 16
        val barW = w - 12
        val gap = 1
        val seg = ((barW - gap * (s.floors - 1)).toFloat() / s.floors).coerceAtLeast(1f)
        for (i in 0 until s.floors) {
            val sx = barX + (i * (seg + gap)).toInt()
            val ex = (barX + (i * (seg + gap) + seg).toInt()).coerceAtLeast(sx + 1)
            val color = when {
                i + 1 < s.floor -> UiKit.GREEN
                i + 1 == s.floor -> if (s.boss) UiKit.GOLD else UiKit.brighten(UiKit.GOLD_DARK, pulse / 2)
                i + 1 == s.floors -> UiKit.RED_DARK
                else -> UiKit.PANEL_LIGHT
            }
            g.fill(sx, barY, ex, barY + 5, UiKit.OUTLINE)
            g.fill(sx, barY, ex, barY + 4, color)
        }

        // Linha 3: tempo e BP
        val elapsed = System.currentTimeMillis() - startedAtLocal
        UiKit.icon(g, UiKit.ICON_CLOCK, x + 5, y + 24, 10)
        g.drawString(font, TimeFormat.duration(elapsed), x + 17, y + 26, UiKit.AQUA, true)
        val bp = Component.translatable("shifutower.hud.bp", s.bpEarned)
        val bpX = x + w - 6 - font.width(bp)
        UiKit.icon(g, UiKit.ICON_COIN, bpX - 12, y + 24, 10)
        g.drawString(font, bp, bpX, y + 26, UiKit.GOLD, true)

        // Nome do adversário embaixo do painel
        if (s.trainerName.isNotBlank()) {
            val vs = Component.translatable("shifutower.hud.vs", s.trainerName)
            val tw = font.width(vs)
            g.fill((g.guiWidth() - tw) / 2 - 4, y + h + 1, (g.guiWidth() + tw) / 2 + 4, y + h + 12, UiKit.withAlpha(UiKit.OUTLINE, 0x99))
            g.drawCenteredString(font, vs, g.guiWidth() / 2, y + h + 3, if (s.boss) UiKit.GOLD else UiKit.MUTED)
        }
    }
}
