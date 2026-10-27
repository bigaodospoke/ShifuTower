package com.shifutower.client.screen

import com.shifutower.ShifuTower
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence

/** Paleta e desenhos básicos da interface da Torre Shifu (roxo profundo + dourado). */
object UiKit {
    val ICONS: ResourceLocation = ShifuTower.id("textures/gui/icones.png")

    const val BG = 0xF0140B26.toInt()
    const val PANEL = 0xFF21143D.toInt()
    const val PANEL_LIGHT = 0xFF2D1C52.toInt()
    const val PANEL_DARK = 0xFF170D2C.toInt()
    const val OUTLINE = 0xFF07030F.toInt()
    const val GOLD = 0xFFF5C542.toInt()
    const val GOLD_DARK = 0xFFA87512.toInt()
    const val GOLD_LIGHT = 0xFFFFE89A.toInt()
    const val RED = 0xFFE5484D.toInt()
    const val RED_DARK = 0xFF8E1F27.toInt()
    const val GREEN = 0xFF5BD47A.toInt()
    const val GREEN_DARK = 0xFF1F7A3C.toInt()
    const val AQUA = 0xFF6FE3F2.toInt()
    const val TEXT = 0xFFF3ECFF.toInt()
    const val MUTED = 0xFFA594C9.toInt()
    const val SHADOW = 0x66000000

    // Índices do atlas textures/gui/icones.png (4x4 ícones de 16px)
    const val ICON_TROPHY = 0
    const val ICON_COIN = 1
    const val ICON_CLOCK = 2
    const val ICON_CROWN = 3
    const val ICON_SWORDS = 4
    const val ICON_BAG = 5
    const val ICON_TOWER = 6
    const val ICON_STAR = 7
    const val ICON_MEDAL_GOLD = 8
    const val ICON_MEDAL_SILVER = 9
    const val ICON_MEDAL_BRONZE = 10
    const val ICON_LOCK = 11
    const val ICON_CHECK = 12
    const val ICON_CROSS = 13
    const val ICON_FLAME = 14
    const val ICON_SKULL = 15

    fun icon(g: GuiGraphics, index: Int, x: Int, y: Int, size: Int = 16) {
        val u = (index % 4) * 16f
        val v = (index / 4) * 16f
        if (size == 16) {
            g.blit(ICONS, x, y, u, v, 16, 16, 64, 64)
            return
        }
        val pose = g.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        pose.scale(size / 16f, size / 16f, 1f)
        g.blit(ICONS, 0, 0, u, v, 16, 16, 64, 64)
        pose.popPose()
    }

    /** Painel com contorno escuro, borda dourada e brilho no topo, cantos "pixelados". */
    fun panel(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int = PANEL, border: Int = GOLD_DARK, highlight: Int = GOLD) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE)
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, border)
        g.fill(x + 2, y + 1, x + w - 2, y + 2, highlight)
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill)
    }

    /** Caixa interna afundada (para listas e estatísticas). */
    fun inset(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, fill: Int = PANEL_DARK) {
        g.fill(x, y, x + w, y + h, OUTLINE)
        g.fill(x + 1, y + 1, x + w, y + h, PANEL_LIGHT)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill)
    }

    fun gradientBar(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, top: Int, bottom: Int) {
        g.fillGradient(x, y, x + w, y + h, top, bottom)
    }

    enum class Style(val top: Int, val bottom: Int, val border: Int, val text: Int) {
        GOLD(0xFFFFD866.toInt(), 0xFFD49A1C.toInt(), 0xFF7A4E05.toInt(), 0xFF3A2200.toInt()),
        RED(0xFFF2666B.toInt(), 0xFFB8262E.toInt(), 0xFF5E0E14.toInt(), 0xFFFFF1F1.toInt()),
        PURPLE(0xFF5B3E99.toInt(), 0xFF3A2468.toInt(), 0xFF1A0E33.toInt(), 0xFFF3ECFF.toInt()),
        GREEN(0xFF6FE08B.toInt(), 0xFF2E9A50.toInt(), 0xFF0F4A22.toInt(), 0xFF06240F.toInt()),
        DISABLED(0xFF4A4458.toInt(), 0xFF35303F.toInt(), 0xFF1A1720.toInt(), 0xFF8E879C.toInt()),
    }

    fun button(
        g: GuiGraphics, font: Font, x: Int, y: Int, w: Int, h: Int, label: Component,
        style: Style, hovered: Boolean, pressedOffset: Boolean = false, icon: Int = -1,
    ) {
        val top = if (hovered && style != Style.DISABLED) brighten(style.top, 24) else style.top
        val bottom = if (hovered && style != Style.DISABLED) brighten(style.bottom, 18) else style.bottom
        val dy = if (pressedOffset) 1 else 0
        g.fill(x + 1, y + dy, x + w - 1, y + h + dy, style.border)
        g.fill(x, y + 1 + dy, x + w, y + h - 1 + dy, style.border)
        g.fillGradient(x + 1, y + 1 + dy, x + w - 1, y + h - 1 + dy, top, bottom)
        g.fill(x + 2, y + 1 + dy, x + w - 2, y + 2 + dy, brighten(top, 40))
        if (style != Style.DISABLED) g.fill(x + 1, y + h - 2 + dy, x + w - 1, y + h - 1 + dy, darken(bottom, 30))

        val textWidth = font.width(label) + if (icon >= 0) 12 else 0
        var tx = x + (w - textWidth) / 2
        val ty = y + (h - 8) / 2 + dy
        if (icon >= 0) {
            icon(g, icon, tx, ty - 1, 10)
            tx += 12
        }
        val shadow = style == Style.RED || style == Style.PURPLE
        g.drawString(font, label, tx, ty, style.text, shadow)
    }

    fun inside(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

    fun brighten(color: Int, amount: Int): Int = adjust(color, amount)

    fun darken(color: Int, amount: Int): Int = adjust(color, -amount)

    private fun adjust(color: Int, amount: Int): Int {
        val a = color ushr 24 and 0xFF
        val r = ((color shr 16 and 0xFF) + amount).coerceIn(0, 255)
        val gC = ((color shr 8 and 0xFF) + amount).coerceIn(0, 255)
        val b = ((color and 0xFF) + amount).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (gC shl 8) or b
    }

    fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    fun trim(font: Font, text: Component, width: Int): FormattedCharSequence {
        if (font.width(text) <= width) return text.visualOrderText
        val ellipsis = Component.literal("…")
        val cut = font.substrByWidth(text, width - font.width(ellipsis))
        return net.minecraft.locale.Language.getInstance().getVisualOrder(
            net.minecraft.network.chat.FormattedText.composite(cut, ellipsis),
        )
    }
}
