package com.shifutower.client.screen

import com.mojang.blaze3d.platform.Lighting
import com.mojang.math.Axis
import com.shifutower.network.RunResultPayload
import com.shifutower.network.TowerActionPayload
import com.shifutower.registry.ModItems
import com.shifutower.util.TimeFormat
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Tela exibida ao terminar o desafio: troféu girando na vitória, resumo do progresso na derrota. */
class ResultScreen(private val result: RunResultPayload) : Screen(Component.translatable("shifutower.resultado.titulo")) {
    private val openedAt = System.currentTimeMillis()
    private val trophy = ItemStack(ModItems.TROPHY)

    private val panelW get() = min(292, width - 12)
    private val panelH get() = min(186, height - 12)
    private val left get() = (width - panelW) / 2
    private val top get() = (height - panelH) / 2

    override fun isPauseScreen() = false

    override fun init() {
        if (result.won) {
            minecraft?.soundManager?.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f))
        }
    }

    private fun buttons(): List<IntArray> {
        val bw = (panelW - 24) / 2
        val by = top + panelH - 26
        return listOf(intArrayOf(left + 8, by, bw, 18), intArrayOf(left + 16 + bw, by, bw, 18))
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        val t = (System.currentTimeMillis() - openedAt) / 1000.0
        val appear = min(1.0, t / 0.25).toFloat()
        val pose = g.pose()
        pose.pushPose()
        pose.translate(0f, (1 - appear) * 12f, 0f)

        if (result.won) drawConfetti(g, t)
        UiKit.panel(g, left, top, panelW, panelH, UiKit.PANEL, if (result.won) UiKit.GOLD_DARK else UiKit.RED_DARK, if (result.won) UiKit.GOLD else UiKit.RED)

        // Faixa do título
        val (bandTop, bandBottom) = if (result.won) 0xFFFFD866.toInt() to 0xFFB8800F.toInt() else 0xFFE5484D.toInt() to 0xFF7A1A22.toInt()
        g.fillGradient(left + 2, top + 2, left + panelW - 2, top + 28, bandTop, bandBottom)
        g.fill(left + 2, top + 28, left + panelW - 2, top + 29, UiKit.OUTLINE)
        val title = Component.translatable(titleKey()).withStyle(ChatFormatting.BOLD)
        val tw = font.width(title)
        val titleColor = if (result.won) 0xFF3A2200.toInt() else 0xFFFFF1F1.toInt()
        g.drawString(font, title, left + (panelW - tw) / 2, top + 11, titleColor, !result.won)
        val sideIcon = if (result.won) UiKit.ICON_CROWN else UiKit.ICON_SKULL
        UiKit.icon(g, sideIcon, left + (panelW - tw) / 2 - 20, top + 7)
        UiKit.icon(g, sideIcon, left + (panelW + tw) / 2 + 4, top + 7)

        if (result.won) drawVictory(g, t) else drawDefeat(g)

        val labels = listOf(Component.translatable("shifutower.resultado.ver_ranking"), Component.translatable("shifutower.resultado.fechar"))
        buttons().forEachIndexed { i, (x, y, w, h) ->
            UiKit.button(
                g, font, x, y, w, h, labels[i], if (i == 0) UiKit.Style.PURPLE else UiKit.Style.GOLD,
                UiKit.inside(mouseX.toDouble(), mouseY.toDouble(), x, y, w, h), icon = if (i == 0) UiKit.ICON_CROWN else -1,
            )
        }
        pose.popPose()
    }

    private fun titleKey(): String = when {
        result.won -> "shifutower.resultado.vitoria"
        result.reason == "desistiu" -> "shifutower.resultado.desistiu"
        result.reason == "saiu" -> "shifutower.resultado.saiu"
        result.reason == "reinicio" -> "shifutower.resultado.reinicio"
        result.reason == "erro" || result.reason == "admin" -> "shifutower.resultado.cancelado"
        else -> "shifutower.resultado.derrota"
    }

    // ------------------------------------------------------------------ vitória

    private fun drawVictory(g: GuiGraphics, t: Double) {
        val cx = left + 72
        val cy = top + 92
        // Raios de luz girando atrás do troféu
        for (i in 0 until 12) {
            val a = t * 0.6 + i * Math.PI / 6
            for (r in 14..44 step 3) {
                val px = cx + (cos(a) * r).toInt()
                val py = cy + (sin(a) * r).toInt()
                val alpha = (120 - r * 2).coerceAtLeast(10)
                g.fill(px - 1, py - 1, px + 1, py + 1, UiKit.withAlpha(UiKit.GOLD_LIGHT, alpha))
            }
        }
        // Pedestal
        g.fill(cx - 30, cy + 34, cx + 30, cy + 40, UiKit.OUTLINE)
        g.fillGradient(cx - 29, cy + 34, cx + 29, cy + 39, 0xFF4A2F7E.toInt(), 0xFF22133F.toInt())
        g.fill(cx - 29, cy + 34, cx + 29, cy + 35, UiKit.GOLD)
        renderSpinningTrophy(g, cx, cy, 64f, t)
        // Estrelinhas orbitando
        for (i in 0 until 6) {
            val a = -t * 1.2 + i * Math.PI / 3
            val px = cx + (cos(a) * 40).toInt() - 5
            val py = cy + (sin(a) * 22).toInt() - 5
            if (((t * 3 + i) % 2) < 1.4) UiKit.icon(g, UiKit.ICON_STAR, px, py, 10)
        }

        var y = top + 38
        val x = left + 140
        val w = panelW - 148
        line(g, x, y, w, UiKit.ICON_CLOCK, Component.translatable("shifutower.resultado.tempo"), Component.literal(TimeFormat.duration(result.timeMs)), UiKit.AQUA)
        y += 24
        if (result.newRecord) {
            val flash = ((sin(t * 8) + 1) * 0.5 * 255).toInt().coerceIn(90, 255)
            UiKit.icon(g, UiKit.ICON_FLAME, x, y - 2, 11)
            g.drawString(font, Component.translatable("shifutower.resultado.recorde").withStyle(ChatFormatting.BOLD), x + 14, y, UiKit.withAlpha(UiKit.GOLD_LIGHT, flash), true)
            y += 13
        }
        val rank = if (result.rank > 0) "#${result.rank}" else "—"
        line(g, x, y, w, UiKit.ICON_CROWN, Component.translatable("shifutower.resultado.ranking"), Component.literal(rank), UiKit.GOLD)
        y += 24
        line(
            g, x, y, w, UiKit.ICON_COIN, Component.translatable("shifutower.resultado.bp"),
            Component.translatable("shifutower.resultado.bp_valor", result.bpEarned, result.totalBp), UiKit.GOLD,
        )
        y += 24
        if (result.trophy) {
            UiKit.icon(g, UiKit.ICON_TROPHY, x, y - 2, 11)
            g.drawString(font, UiKit.trim(font, Component.translatable("shifutower.resultado.trofeu"), w - 14), x + 14, y, UiKit.GREEN, true)
        }
    }

    private fun renderSpinningTrophy(g: GuiGraphics, cx: Int, cy: Int, scale: Float, t: Double) {
        val mc = Minecraft.getInstance()
        val pose = g.pose()
        pose.pushPose()
        pose.translate(cx.toFloat(), cy.toFloat() + (sin(t * 2) * 2).toFloat(), 150f)
        pose.scale(scale, -scale, scale)
        pose.mulPose(Axis.XP.rotationDegrees(12f))
        pose.mulPose(Axis.YP.rotationDegrees((t * 70 % 360).toFloat()))
        Lighting.setupForEntityInInventory()
        mc.itemRenderer.renderStatic(
            trophy, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            pose, g.bufferSource(), mc.level, 0,
        )
        g.flush()
        Lighting.setupFor3DItems()
        pose.popPose()
    }

    private fun drawConfetti(g: GuiGraphics, t: Double) {
        val colors = intArrayOf(UiKit.GOLD, UiKit.GOLD_LIGHT, UiKit.RED, UiKit.AQUA, UiKit.GREEN, 0xFFB98CFF.toInt())
        for (i in 0 until 70) {
            val seed = i * 9973
            val speed = 30 + seed % 40
            val x = ((seed % 1000) / 1000.0 * width + sin(t * 2 + i) * 12).toInt()
            val y = (((t * speed) + seed % height) % (height + 20)).toInt() - 10
            val size = 2 + seed % 2
            g.fill(x, y, x + size, y + size + 1, UiKit.withAlpha(colors[i % colors.size], 220))
        }
    }

    // ------------------------------------------------------------------ derrota

    private fun drawDefeat(g: GuiGraphics) {
        // Mini torre com o progresso alcançado
        val floors = result.floors.coerceAtLeast(1)
        val tx = left + 12
        val ty = top + 36
        val th = panelH - 72
        UiKit.inset(g, tx, ty, 100, th, 0xFF0F0820.toInt())
        val fh = ((th - 16) / floors).coerceIn(3, 12)
        val baseY = ty + th - 6
        for (i in 0 until floors) {
            val floor = i + 1
            val fw = 64 - i * 24 / (floors - 1).coerceAtLeast(1)
            val fx = tx + 50 - fw / 2
            val fy = baseY - (i + 1) * fh
            val color = when {
                floor <= result.floorReached -> UiKit.GREEN_DARK
                floor == result.floorReached + 1 -> UiKit.RED
                else -> 0xFF2A2140.toInt()
            }
            g.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh, UiKit.OUTLINE)
            g.fill(fx, fy, fx + fw, fy + fh - 1, color)
            if (floor == result.floorReached + 1 && fh >= 8) UiKit.icon(g, UiKit.ICON_SKULL, tx + 50 - 4, fy + (fh - 8) / 2, 8)
        }

        var y = top + 40
        val x = left + 122
        val w = panelW - 130
        line(
            g, x, y, w, UiKit.ICON_TOWER, Component.translatable("shifutower.resultado.alcancou"),
            Component.translatable("shifutower.resultado.andar_valor", result.floorReached, result.floors), UiKit.TEXT,
        )
        y += 26
        line(
            g, x, y, w, UiKit.ICON_COIN, Component.translatable("shifutower.resultado.bp"),
            Component.translatable("shifutower.resultado.bp_valor", result.bpEarned, result.totalBp), UiKit.GOLD,
        )
        y += 26
        line(g, x, y, w, UiKit.ICON_CLOCK, Component.translatable("shifutower.resultado.tempo"), Component.literal(TimeFormat.duration(result.timeMs)), UiKit.AQUA)
        y += 28
        val tip = if (result.reason == "reinicio" || result.reason == "admin") "shifutower.resultado.dica_devolvida" else "shifutower.resultado.dica"
        font.split(Component.translatable(tip), w).forEachIndexed { i, seq ->
            g.drawString(font, seq, x, y + i * 10, UiKit.MUTED, false)
        }
    }

    private fun line(g: GuiGraphics, x: Int, y: Int, w: Int, icon: Int, label: Component, value: Component, color: Int) {
        UiKit.icon(g, icon, x, y)
        g.drawString(font, label, x + 20, y, UiKit.MUTED, false)
        g.drawString(font, UiKit.trim(font, value, w - 20), x + 20, y + 10, color, true)
    }

    // ------------------------------------------------------------------ entrada

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            buttons().forEachIndexed { i, (x, y, w, h) ->
                if (UiKit.inside(mouseX, mouseY, x, y, w, h)) {
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f))
                    if (i == 0) ClientPlayNetworking.send(TowerActionPayload(TowerActionPayload.REFRESH, 1, 0)) else onClose()
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
