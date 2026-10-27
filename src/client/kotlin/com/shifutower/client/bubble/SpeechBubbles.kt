package com.shifutower.client.bubble

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import com.shifutower.network.SpeechPayload
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Matrix4f

/**
 * Balões de fala estilo quadrinho que flutuam sobre a cabeça dos NPCs da torre.
 * O texto aparece letra por letra, com o "gibber" do Cobblemon tocando enquanto o NPC fala.
 */
object SpeechBubbles {
    private class Bubble(
        val entityId: Int,
        val speaker: Component,
        val text: String,
        val totalTicks: Int,
        val style: Int,
    ) {
        var age = 0
        var lines: List<String> = emptyList()
        val revealed: Int get() = (age * CHARS_PER_TICK).toInt().coerceAtMost(text.length)
    }

    private class Look(val fill: Int, val outline: Int, val speaker: Int, val text: Int)

    private val LOOKS = mapOf(
        SpeechPayload.STYLE_TRAINER to Look(0xF5FFFFFF.toInt(), 0xFF1B1030.toInt(), 0xFF7B3FE4.toInt(), 0xFF26203A.toInt()),
        SpeechPayload.STYLE_BOSS to Look(0xF51A1030.toInt(), 0xFFF5C542.toInt(), 0xFFF5C542.toInt(), 0xFFF3ECFF.toInt()),
        SpeechPayload.STYLE_RECEPTIONIST to Look(0xF5E9FBFF.toInt(), 0xFF1E6F8F.toInt(), 0xFF1E6F8F.toInt(), 0xFF1B2A33.toInt()),
    )

    private const val CHARS_PER_TICK = 1.6
    private const val MAX_WIDTH = 150
    private const val PAD = 5
    private const val LINE_H = 10
    private const val SCALE = 0.025f

    private val bubbles = LinkedHashMap<Int, Bubble>()
    private val buffers: MultiBufferSource.BufferSource = MultiBufferSource.immediate(ByteBufferBuilder(4096))
    private val random = RandomSource.create()
    private val gibber = ResourceLocation.fromNamespaceAndPath("cobblemon", "entity.npc.gibber.generic")

    fun show(payload: SpeechPayload) {
        val text = payload.text.string
        val readingTime = (text.length / CHARS_PER_TICK).toInt() + 40
        bubbles[payload.entityId] = Bubble(payload.entityId, payload.speaker, text, maxOf(payload.ticks, readingTime), payload.style)
    }

    fun clear() = bubbles.clear()

    fun tick(mc: Minecraft) {
        if (bubbles.isEmpty()) return
        val level = mc.level ?: run { bubbles.clear(); return }
        val iterator = bubbles.values.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            val before = bubble.revealed
            bubble.age++
            if (bubble.age > bubble.totalTicks) {
                iterator.remove()
                continue
            }
            val after = bubble.revealed
            // Um "blip" a cada 3 letras novas, como nos jogos
            if (after > before && after < bubble.text.length && after / 3 != before / 3) {
                val entity = level.getEntity(bubble.entityId) ?: continue
                val sound = BuiltInRegistries.SOUND_EVENT.get(gibber) ?: continue
                val pitch = if (bubble.style == SpeechPayload.STYLE_BOSS) 0.7f else 1.0f + random.nextFloat() * 0.25f
                mc.soundManager.play(
                    SimpleSoundInstance(sound, SoundSource.NEUTRAL, 0.35f, pitch, random, entity.x, entity.eyeY, entity.z),
                )
            }
        }
    }

    fun render(context: WorldRenderContext) {
        if (bubbles.isEmpty()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera()
        val partial = context.tickCounter().getGameTimeDeltaPartialTick(false)
        val font = mc.font

        for (bubble in bubbles.values) {
            val entity = level.getEntity(bubble.entityId) ?: continue
            if (bubble.lines.isEmpty()) {
                bubble.lines = font.splitter.splitLines(bubble.text, MAX_WIDTH, Style.EMPTY).map { it.string }
            }
            val pos = entity.getPosition(partial)
            val cam = camera.position
            if (pos.distanceToSqr(cam) > 48.0 * 48.0) continue

            poseStack.pushPose()
            poseStack.translate(pos.x - cam.x, pos.y + entity.bbHeight + 0.85 - cam.y, pos.z - cam.z)
            poseStack.mulPose(camera.rotation())
            val pop = ((bubble.age + partial) / 4f).coerceIn(0f, 1f)
            val s = SCALE * (0.6f + 0.4f * easeOutBack(pop))
            poseStack.scale(s, -s, s)
            drawBubble(poseStack.last().pose(), buffers, font, bubble, partial)
            poseStack.popPose()
        }
        buffers.endBatch()
    }

    private fun drawBubble(matrix: Matrix4f, buffers: MultiBufferSource.BufferSource, font: Font, bubble: Bubble, partial: Float) {
        val look = LOOKS[bubble.style] ?: LOOKS.getValue(SpeechPayload.STYLE_TRAINER)
        val fadeTicks = 10f
        val remaining = bubble.totalTicks - (bubble.age + partial)
        val alpha = (remaining / fadeTicks).coerceIn(0f, 1f)
        if (alpha <= 0.02f) return

        val speakerW = font.width(bubble.speaker)
        val textW = bubble.lines.maxOfOrNull { font.width(it) } ?: 0
        val w = maxOf(speakerW, textW, 40) + PAD * 2
        val h = PAD * 2 + LINE_H + bubble.lines.size * LINE_H
        val tail = 6
        val x1 = -w / 2f
        val x2 = w / 2f
        val y2 = -tail.toFloat()
        val y1 = y2 - h

        val bg = buffers.getBuffer(RenderType.textBackground())
        val fill = fade(look.fill, alpha)
        val outline = fade(look.outline, alpha)
        val light = LightTexture.FULL_BRIGHT

        // Contorno (1px maior) + preenchimento, cantos arredondados no estilo pixel.
        // Depois de girar para a câmera, z maior fica MAIS PERTO dela: o fundo vai atrás (z negativo) e o texto (z 0) na frente.
        rounded(bg, matrix, x1 - 1, y1 - 1, x2 + 1, y2 + 1, outline, -0.03f, light)
        rounded(bg, matrix, x1, y1, x2, y2, fill, -0.02f, light)
        // Rabinho do balão apontando para a cabeça do NPC
        for (row in 0 until tail) {
            val half = (tail - row) / 2f
            val cx = -6f + row * 0.5f
            rect(bg, matrix, cx - half - 1, y2 + row - 1, cx + half + 1, y2 + row + 1, outline, -0.03f, light)
            rect(bg, matrix, cx - half, y2 + row - 1, cx + half, y2 + row, fill, -0.02f, light)
        }
        // Faixa separando o nome do texto
        rect(bg, matrix, x1 + PAD, y1 + PAD + LINE_H - 1, x2 - PAD, y1 + PAD + LINE_H, fade(look.speaker, alpha * 0.35f), -0.015f, light)

        val textAlpha = (alpha * 255).toInt().coerceIn(8, 255)
        font.drawInBatch(
            bubble.speaker.copy().withStyle { it.withBold(true) }, x1 + PAD, y1 + PAD, (textAlpha shl 24) or (look.speaker and 0xFFFFFF),
            false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light,
        )
        var left = bubble.revealed
        var y = y1 + PAD + LINE_H + 1
        for (line in bubble.lines) {
            if (left <= 0) break
            val shown = line.take(left)
            left -= line.length + 1
            font.drawInBatch(
                shown, x1 + PAD, y, (textAlpha shl 24) or (look.text and 0xFFFFFF),
                false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light,
            )
            y += LINE_H
        }
    }

    private fun rounded(vc: VertexConsumer, m: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, z: Float, light: Int) {
        rect(vc, m, x1 + 1, y1, x2 - 1, y2, color, z, light)
        rect(vc, m, x1, y1 + 1, x1 + 1, y2 - 1, color, z, light)
        rect(vc, m, x2 - 1, y1 + 1, x2, y2 - 1, color, z, light)
    }

    private fun rect(vc: VertexConsumer, m: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, z: Float, light: Int) {
        vc.addVertex(m, x1, y1, z).setColor(color).setLight(light)
        vc.addVertex(m, x1, y2, z).setColor(color).setLight(light)
        vc.addVertex(m, x2, y2, z).setColor(color).setLight(light)
        vc.addVertex(m, x2, y1, z).setColor(color).setLight(light)
    }

    private fun fade(color: Int, alpha: Float): Int {
        val a = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1
        val u = t - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }

}
