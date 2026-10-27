package com.shifutower.client.screen

import com.shifutower.network.ShopView
import com.shifutower.network.TowerActionPayload
import com.shifutower.network.TowerSnapshot
import com.shifutower.util.TimeFormat
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tela principal da torre: abas Torre, Ranking e Loja. */
class TowerScreen(private var data: TowerSnapshot, private var tab: Int) : Screen(Component.translatable("shifutower.gui.titulo")) {
    private var receivedAt = System.currentTimeMillis()
    private var shopScroll = 0.0
    private var tooltip: List<Component>? = null

    private val panelW get() = min(344, width - 12)
    private val panelH get() = min(228, height - 12)
    private val left get() = (width - panelW) / 2
    private val top get() = (height - panelH) / 2
    private val contentX get() = left + 7
    private val contentY get() = top + 50
    private val contentW get() = panelW - 14
    private val contentH get() = panelH - 57

    fun update(snapshot: TowerSnapshot, newTab: Int) {
        data = snapshot
        tab = newTab
        receivedAt = System.currentTimeMillis()
        shopScroll = shopScroll.coerceIn(0.0, maxShopScroll().toDouble())
    }

    override fun isPauseScreen() = false

    // ================================================================== desenho

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        tooltip = null
        UiKit.panel(g, left, top, panelW, panelH, UiKit.PANEL)
        drawHeader(g)
        drawTabs(g, mouseX, mouseY)
        when (tab) {
            0 -> drawTowerTab(g, mouseX, mouseY)
            1 -> drawRankingTab(g)
            else -> drawShopTab(g, mouseX, mouseY)
        }
        tooltip?.let { g.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    private fun drawHeader(g: GuiGraphics) {
        g.fillGradient(left + 2, top + 2, left + panelW - 2, top + 25, 0xFF3D2575.toInt(), 0xFF21143D.toInt())
        g.fill(left + 2, top + 25, left + panelW - 2, top + 26, UiKit.GOLD_DARK)
        UiKit.icon(g, UiKit.ICON_TOWER, left + 6, top + 5)
        g.drawString(font, Component.translatable("shifutower.gui.titulo").withStyle(ChatFormatting.BOLD), left + 26, top + 6, UiKit.GOLD, true)
        g.drawString(font, Component.translatable("shifutower.gui.subtitulo"), left + 26, top + 15, UiKit.MUTED, false)

        val bp = Component.translatable("shifutower.gui.bp", data.bp)
        val bw = font.width(bp) + 24
        val bx = left + panelW - bw - 7
        UiKit.inset(g, bx, top + 6, bw, 15)
        UiKit.icon(g, UiKit.ICON_COIN, bx + 3, top + 8, 11)
        g.drawString(font, bp, bx + 18, top + 10, UiKit.GOLD, true)
    }

    private fun tabRect(i: Int): IntArray {
        val tw = (contentW - 8) / 3
        return intArrayOf(contentX + i * (tw + 4), top + 30, tw, 16)
    }

    private fun drawTabs(g: GuiGraphics, mx: Int, my: Int) {
        val labels = listOf("shifutower.gui.aba.torre", "shifutower.gui.aba.ranking", "shifutower.gui.aba.loja")
        val icons = listOf(UiKit.ICON_SWORDS, UiKit.ICON_CROWN, UiKit.ICON_BAG)
        for (i in 0..2) {
            val (x, y, w, h) = tabRect(i).toList()
            val selected = tab == i
            UiKit.button(
                g, font, x, y, w, h, Component.translatable(labels[i]),
                if (selected) UiKit.Style.GOLD else UiKit.Style.PURPLE,
                hovered = !selected && UiKit.inside(mx.toDouble(), my.toDouble(), x, y, w, h), icon = icons[i],
            )
        }
    }

    // ------------------------------------------------------------------ aba Torre

    private fun startButtonRect(): IntArray {
        val x = contentX + 104
        val w = contentW - 104
        return intArrayOf(x, contentY + contentH - 22, w, 22)
    }

    private fun drawTowerTab(g: GuiGraphics, mx: Int, my: Int) {
        drawTowerIllustration(g, mx, my)

        val x = contentX + 104
        val w = contentW - 104
        var y = contentY

        // Status do dia
        UiKit.inset(g, x, y, w, 28)
        val remaining = data.msUntilReset - (System.currentTimeMillis() - receivedAt)
        val (statusIcon, statusText, statusColor) = when {
            data.inRun -> Triple(UiKit.ICON_FLAME, Component.translatable("shifutower.gui.status.andamento"), UiKit.AQUA)
            data.attemptsLeft > 0 -> Triple(
                UiKit.ICON_CHECK,
                Component.translatable("shifutower.gui.status.disponivel", data.attemptsLeft, data.attemptsPerDay), UiKit.GREEN,
            )
            else -> Triple(UiKit.ICON_LOCK, Component.translatable("shifutower.gui.status.volta", TimeFormat.countdown(remaining)), UiKit.RED)
        }
        UiKit.icon(g, statusIcon, x + 5, y + 6)
        g.drawString(font, Component.translatable("shifutower.gui.status.titulo"), x + 26, y + 5, UiKit.MUTED, false)
        g.drawString(font, statusText, x + 26, y + 16, statusColor, true)
        y += 32

        // Estatísticas 2x2
        val cw = (w - 4) / 2
        val rankText = if (data.rank > 0) "#${data.rank}" else "—"
        stat(g, x, y, cw, UiKit.ICON_CLOCK, "shifutower.gui.stat.tempo", TimeFormat.duration(data.bestTimeMs), UiKit.AQUA)
        stat(g, x + cw + 4, y, cw, UiKit.ICON_CROWN, "shifutower.gui.stat.ranking", rankText, UiKit.GOLD)
        y += 28
        stat(g, x, y, cw, UiKit.ICON_TROPHY, "shifutower.gui.stat.vitorias", "${data.clears}  (${data.runs} desafios)", UiKit.GOLD_LIGHT)
        stat(g, x + cw + 4, y, cw, UiKit.ICON_TOWER, "shifutower.gui.stat.andar", "${data.bestFloor}/${data.floors}", UiKit.TEXT)
        y += 30

        // Recompensas e chefe
        UiKit.icon(g, UiKit.ICON_COIN, x, y - 1, 10)
        g.drawString(font, Component.translatable("shifutower.gui.recompensa", data.maxBp), x + 13, y, UiKit.GOLD, false)
        y += 11
        if (data.bossName.isNotBlank()) {
            UiKit.icon(g, UiKit.ICON_SKULL, x, y - 1, 10)
            g.drawString(font, UiKit.trim(font, Component.translatable("shifutower.gui.chefe", data.bossName), w - 14), x + 13, y, UiKit.MUTED, false)
        }

        // Botão principal
        val (bx, by, bw, bh) = startButtonRect().toList()
        val hovered = UiKit.inside(mx.toDouble(), my.toDouble(), bx, by, bw, bh)
        when {
            data.inRun -> UiKit.button(g, font, bx, by, bw, bh, Component.translatable("shifutower.gui.botao.desistir"), UiKit.Style.RED, hovered, icon = UiKit.ICON_CROSS)
            data.attemptsLeft > 0 -> {
                val glow = ((sin(System.currentTimeMillis() / 300.0) + 1) * 0.5 * 90).toInt()
                g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, UiKit.withAlpha(UiKit.GOLD_LIGHT, glow))
                UiKit.button(g, font, bx, by, bw, bh, Component.translatable("shifutower.gui.botao.iniciar").withStyle(ChatFormatting.BOLD), UiKit.Style.GOLD, hovered, icon = UiKit.ICON_SWORDS)
            }
            else -> UiKit.button(g, font, bx, by, bw, bh, Component.translatable("shifutower.gui.botao.amanha"), UiKit.Style.DISABLED, false, icon = UiKit.ICON_LOCK)
        }
    }

    private fun stat(g: GuiGraphics, x: Int, y: Int, w: Int, icon: Int, label: String, value: String, color: Int) {
        UiKit.inset(g, x, y, w, 24)
        UiKit.icon(g, icon, x + 4, y + 4)
        g.drawString(font, Component.translatable(label), x + 23, y + 4, UiKit.MUTED, false)
        g.drawString(font, UiKit.trim(font, Component.literal(value), w - 26), x + 23, y + 14, color, true)
    }

    private fun floorColor(floor: Int): Int {
        if (floor >= data.floors) return 0xFF5A2A8C.toInt()
        val p = floor.toDouble() / data.floors
        return when {
            p <= 0.25 -> 0xFF7C7A8A.toInt()
            p <= 0.5 -> 0xFF4A5063.toInt()
            p <= 0.75 -> 0xFFB46A42.toInt()
            else -> 0xFFE2DACB.toInt()
        }
    }

    private fun drawTowerIllustration(g: GuiGraphics, mx: Int, my: Int) {
        val x = contentX
        val y = contentY
        val w = 98
        val h = contentH
        UiKit.inset(g, x, y, w, h, 0xFF0F0820.toInt())
        // Céu estrelado
        for (i in 0 until 18) {
            val sx = x + 4 + (i * 37 % (w - 8))
            val sy = y + 4 + (i * 53 % (h - 30))
            val twinkle = ((sin(System.currentTimeMillis() / 400.0 + i) + 1) * 60).toInt() + 60
            g.fill(sx, sy, sx + 1, sy + 1, UiKit.withAlpha(0xFFFFFF, twinkle))
        }

        val floors = data.floors
        val roof = 16
        val usable = h - roof - 10
        val floorH = (usable / floors).coerceIn(3, 14)
        val towerH = floorH * floors
        val baseY = y + h - 6
        val cx = x + w / 2

        g.fill(x + 6, baseY, x + w - 6, baseY + 2, 0xFF3B2F55.toInt())
        for (i in 0 until floors) {
            val floor = i + 1
            val fw = 70 - (i * 26 / max(1, floors - 1))
            val fx = cx - fw / 2
            val fy = baseY - (i + 1) * floorH
            val boss = floor == floors
            val cleared = floor <= data.bestFloor
            g.fill(fx - 1, fy - 1, fx + fw + 1, fy + floorH, UiKit.OUTLINE)
            g.fillGradient(fx, fy, fx + fw, fy + floorH - 1, UiKit.brighten(floorColor(floor), 20), UiKit.darken(floorColor(floor), 25))
            if (boss) g.fill(fx, fy, fx + fw, fy + 1, UiKit.GOLD)
            // Janelinhas: acesas nos andares que você já venceu
            if (floorH >= 5) {
                val windows = (fw - 6) / 7
                for (wi in 0 until windows) {
                    val wx = fx + 4 + wi * 7
                    val wy = fy + floorH / 2 - 2
                    g.fill(wx, wy, wx + 3, wy + 3, if (cleared) UiKit.GOLD_LIGHT else 0xFF1A1230.toInt())
                }
            }
            if (UiKit.inside(mx.toDouble(), my.toDouble(), fx, fy, fw, floorH)) {
                g.fill(fx, fy, fx + fw, fy + floorH - 1, 0x40FFFFFF)
                tooltip = floorTooltip(floor)
            }
        }
        // Telhado + coroa do chefe
        val topY = baseY - towerH
        val roofW = 70 - 26 + 6
        for (r in 0 until roof - 4) {
            val rw = (roofW * (roof - 4 - r) / (roof - 4)).coerceAtLeast(2)
            g.fill(cx - rw / 2, topY - r - 1, cx + rw / 2, topY - r, if (r == 0) UiKit.GOLD else 0xFF6B2FA3.toInt())
        }
        UiKit.icon(g, UiKit.ICON_CROWN, cx - 6, topY - roof - 8, 12)
        g.drawCenteredString(font, Component.translatable("shifutower.gui.andares", floors), cx, y + 3, UiKit.MUTED)
    }

    private fun floorTooltip(floor: Int): List<Component> {
        val tier = data.tiers.firstOrNull { floor in it.fromFloor..it.toFloor } ?: data.tiers.lastOrNull()
        val lines = mutableListOf<Component>(
            Component.translatable(if (floor == data.floors) "shifutower.gui.tip.chefe" else "shifutower.gui.tip.andar", floor)
                .withStyle(if (floor == data.floors) ChatFormatting.GOLD else ChatFormatting.YELLOW),
        )
        if (tier != null) {
            lines += Component.translatable("shifutower.gui.tip.faixa", tier.name).withStyle(ChatFormatting.GRAY)
            lines += Component.translatable("shifutower.gui.tip.time", tier.teamSize, tier.level).withStyle(ChatFormatting.GRAY)
        }
        data.floorBp.getOrNull(floor - 1)?.let { lines += Component.translatable("shifutower.gui.tip.bp", it).withStyle(ChatFormatting.GOLD) }
        if (floor <= data.bestFloor) lines += Component.translatable("shifutower.gui.tip.vencido").withStyle(ChatFormatting.GREEN)
        return lines
    }

    // ------------------------------------------------------------------ aba Ranking

    private fun drawRankingTab(g: GuiGraphics) {
        val x = contentX
        val y = contentY
        val w = contentW
        val h = contentH - 18
        UiKit.inset(g, x, y, w, h)
        val colPos = x + 6
        val colName = x + 34
        val colTime = x + w - 120
        val colDate = x + w - 62
        g.drawString(font, "#", colPos + 4, y + 4, UiKit.MUTED, false)
        g.drawString(font, Component.translatable("shifutower.gui.rank.treinador"), colName, y + 4, UiKit.MUTED, false)
        g.drawString(font, Component.translatable("shifutower.gui.rank.tempo"), colTime, y + 4, UiKit.MUTED, false)
        g.drawString(font, Component.translatable("shifutower.gui.rank.data"), colDate, y + 4, UiKit.MUTED, false)
        g.fill(x + 3, y + 14, x + w - 3, y + 15, UiKit.GOLD_DARK)

        if (data.ranking.isEmpty()) {
            UiKit.icon(g, UiKit.ICON_TROPHY, x + w / 2 - 8, y + h / 2 - 18)
            g.drawCenteredString(font, Component.translatable("shifutower.gui.rank.vazio"), x + w / 2, y + h / 2 + 2, UiKit.MUTED)
        }
        val rowH = 14
        val maxRows = (h - 18) / rowH
        data.ranking.take(maxRows).forEachIndexed { i, entry ->
            val ry = y + 17 + i * rowH
            val bg = when {
                entry.self -> 0x553A2A00
                i % 2 == 0 -> 0x22FFFFFF
                else -> 0x00000000
            }
            g.fill(x + 2, ry, x + w - 2, ry + rowH - 1, bg)
            if (entry.self) {
                g.fill(x + 2, ry, x + 3, ry + rowH - 1, UiKit.GOLD)
            }
            val pos = i + 1
            when (pos) {
                1 -> UiKit.icon(g, UiKit.ICON_MEDAL_GOLD, colPos, ry + 1, 12)
                2 -> UiKit.icon(g, UiKit.ICON_MEDAL_SILVER, colPos, ry + 1, 12)
                3 -> UiKit.icon(g, UiKit.ICON_MEDAL_BRONZE, colPos, ry + 1, 12)
                else -> g.drawString(font, "$pos", colPos + 3, ry + 3, UiKit.MUTED, false)
            }
            val nameColor = when (pos) {
                1 -> UiKit.GOLD
                2 -> 0xFFD8DEE9.toInt()
                3 -> 0xFFE09A5B.toInt()
                else -> UiKit.TEXT
            }
            g.drawString(font, UiKit.trim(font, Component.literal(entry.name), colTime - colName - 6), colName, ry + 3, nameColor, true)
            g.drawString(font, TimeFormat.duration(entry.timeMs), colTime, ry + 3, UiKit.AQUA, true)
            g.drawString(font, entry.date, colDate, ry + 3, UiKit.MUTED, false)
        }

        // Rodapé com a sua posição
        val fy = y + h + 4
        val footer = if (data.rank > 0) {
            Component.translatable("shifutower.gui.rank.voce", data.rank, TimeFormat.duration(data.bestTimeMs))
        } else {
            Component.translatable("shifutower.gui.rank.voce_nao")
        }
        UiKit.icon(g, UiKit.ICON_STAR, x, fy, 11)
        g.drawString(font, footer, x + 14, fy + 2, if (data.rank > 0) UiKit.GOLD else UiKit.MUTED, true)
    }

    // ------------------------------------------------------------------ aba Loja

    private val shopTop get() = contentY + 16
    private val shopH get() = contentH - 16
    private val shopCols get() = if (contentW >= 300) 3 else 2
    private val cardW get() = (contentW - 6 - (shopCols - 1) * 4) / shopCols
    private val cardH = 46

    private fun shopRows() = (data.shop.size + shopCols - 1) / shopCols

    private fun maxShopScroll(): Int = max(0, shopRows() * (cardH + 4) - 4 - (shopH - 4))

    private fun cardPos(i: Int): IntArray {
        val col = i % shopCols
        val row = i / shopCols
        return intArrayOf(contentX + 2 + col * (cardW + 4), shopTop + 2 + row * (cardH + 4) - shopScroll.toInt())
    }

    private fun buyRect(i: Int): IntArray {
        val (cx, cy) = cardPos(i).toList()
        return intArrayOf(cx + cardW - 48, cy + cardH - 18, 44, 14)
    }

    private fun drawShopTab(g: GuiGraphics, mx: Int, my: Int) {
        g.drawString(font, Component.translatable("shifutower.gui.loja.dica"), contentX + 2, contentY + 3, UiKit.MUTED, false)
        UiKit.inset(g, contentX, shopTop, contentW, shopH)
        if (data.shop.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("shifutower.gui.loja.vazia"), contentX + contentW / 2, shopTop + shopH / 2 - 4, UiKit.MUTED)
            return
        }
        g.enableScissor(contentX + 1, shopTop + 1, contentX + contentW - 1, shopTop + shopH - 1)
        val inArea = UiKit.inside(mx.toDouble(), my.toDouble(), contentX, shopTop, contentW, shopH)
        data.shop.forEachIndexed { i, item ->
            val (cx, cy) = cardPos(i).toList()
            if (cy + cardH < shopTop || cy > shopTop + shopH) return@forEachIndexed
            drawCard(g, item, i, cx, cy, mx, my, inArea)
        }
        g.disableScissor()

        // Barra de rolagem
        val maxScroll = maxShopScroll()
        if (maxScroll > 0) {
            val trackX = contentX + contentW - 4
            val thumbH = max(12, (shopH - 4) * (shopH - 4) / (shopRows() * (cardH + 4)))
            val thumbY = shopTop + 2 + ((shopH - 4 - thumbH) * (shopScroll / maxScroll)).toInt()
            g.fill(trackX, shopTop + 2, trackX + 2, shopTop + shopH - 2, 0x40FFFFFF)
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, UiKit.GOLD)
        }
    }

    private fun drawCard(g: GuiGraphics, item: ShopView, i: Int, cx: Int, cy: Int, mx: Int, my: Int, inArea: Boolean) {
        val soldOut = item.limit > 0 && item.bought >= item.limit
        val affordable = data.bp >= item.price
        val hoverCard = inArea && UiKit.inside(mx.toDouble(), my.toDouble(), cx, cy, cardW, cardH)
        UiKit.panel(
            g, cx, cy, cardW, cardH,
            if (hoverCard) UiKit.PANEL_LIGHT else UiKit.PANEL,
            if (soldOut) 0xFF3A3346.toInt() else UiKit.GOLD_DARK,
            if (soldOut) 0xFF4A4458.toInt() else UiKit.GOLD,
        )
        // Pedestal + item ampliado
        g.fill(cx + 5, cy + 6, cx + 31, cy + 32, 0xFF120A22.toInt())
        g.fill(cx + 5, cy + 31, cx + 31, cy + 32, UiKit.GOLD_DARK)
        val pose = g.pose()
        pose.pushPose()
        pose.translate((cx + 6).toFloat(), (cy + 7).toFloat(), 0f)
        pose.scale(1.5f, 1.5f, 1f)
        g.renderItem(item.stack, 0, 0)
        g.renderItemDecorations(font, item.stack, 0, 0)
        pose.popPose()

        val textX = cx + 35
        val textW = cardW - 39
        g.drawString(font, UiKit.trim(font, item.name, textW), textX, cy + 6, UiKit.TEXT, true)
        UiKit.icon(g, UiKit.ICON_COIN, textX, cy + 16, 9)
        g.drawString(font, Component.translatable("shifutower.gui.bp", item.price), textX + 11, cy + 17, if (affordable) UiKit.GOLD else UiKit.RED, true)
        if (item.limit > 0) {
            g.drawString(font, "${item.bought}/${item.limit}", cx + 6, cy + cardH - 11, if (soldOut) UiKit.RED else UiKit.MUTED, false)
        }

        val (bx, by, bw, bh) = buyRect(i).toList()
        val hoverBuy = inArea && UiKit.inside(mx.toDouble(), my.toDouble(), bx, by, bw, bh)
        val (label, style) = when {
            soldOut -> Component.translatable("shifutower.gui.loja.esgotado") to UiKit.Style.DISABLED
            !affordable -> Component.translatable("shifutower.gui.loja.comprar") to UiKit.Style.DISABLED
            else -> Component.translatable("shifutower.gui.loja.comprar") to UiKit.Style.GREEN
        }
        UiKit.button(g, font, bx, by, bw, bh, label, style, hoverBuy)

        if (inArea && UiKit.inside(mx.toDouble(), my.toDouble(), cx + 5, cy + 6, 26, 26)) {
            val lines = getTooltipFromItem(Minecraft.getInstance(), item.stack).toMutableList()
            if (item.description.isNotBlank()) item.description.split("\\n", "\n").forEach { lines += Component.literal(it).withStyle(ChatFormatting.GRAY) }
            lines += Component.translatable("shifutower.gui.loja.preco", item.price).withStyle(ChatFormatting.GOLD)
            tooltip = lines
        }
    }

    // ================================================================== entrada

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            for (i in 0..2) {
                val (x, y, w, h) = tabRect(i).toList()
                if (UiKit.inside(mouseX, mouseY, x, y, w, h) && tab != i) {
                    tab = i
                    click()
                    return true
                }
            }
            when (tab) {
                0 -> {
                    val (bx, by, bw, bh) = startButtonRect().toList()
                    if (UiKit.inside(mouseX, mouseY, bx, by, bw, bh)) {
                        when {
                            data.inRun -> send(TowerActionPayload.FORFEIT, 0, 0)
                            data.attemptsLeft > 0 -> send(TowerActionPayload.START, 0, 0)
                            else -> return true
                        }
                        click()
                        onClose()
                        return true
                    }
                }
                2 -> if (UiKit.inside(mouseX, mouseY, contentX, shopTop, contentW, shopH)) {
                    data.shop.forEachIndexed { i, item ->
                        val (bx, by, bw, bh) = buyRect(i).toList()
                        if (UiKit.inside(mouseX, mouseY, bx, by, bw, bh)) {
                            val soldOut = item.limit > 0 && item.bought >= item.limit
                            if (!soldOut && data.bp >= item.price) {
                                send(TowerActionPayload.BUY, item.index, item.price)
                                click()
                            }
                            return true
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (tab == 2) {
            shopScroll = (shopScroll - scrollY * 18).coerceIn(0.0, maxShopScroll().toDouble())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Teclas 1, 2 e 3 trocam de aba
        if (keyCode in 49..51) {
            tab = keyCode - 49
            click()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun send(action: Int, arg: Int, arg2: Int) {
        // fora do mundo (ex.: o servidor caiu com a tela aberta) não há para quem mandar
        if (Minecraft.getInstance().connection == null) return onClose()
        ClientPlayNetworking.send(TowerActionPayload(action, arg, arg2))
    }

    private fun click() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }
}
