package com.shifutower.tower

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * Gera a arena de cada andar na dimensão da torre.
 * Cada jogador usa uma "coluna" própria (slot); os andares ficam empilhados de 20 em 20 blocos,
 * então o jogador realmente vê a torre subindo.
 */
object ArenaBuilder {
    private const val RADIUS = 10
    private const val BASE_Y = 64
    private const val FLOOR_SPACING = 20
    private const val SLOT_SPACING = 256

    private class Palette(
        val floor: BlockState,
        val ring: BlockState,
        val trim: BlockState,
        val pillar: BlockState,
        val pillarTop: BlockState,
        val underside: BlockState,
        val pillarHeight: Int,
    )

    private val STONE = Palette(
        Blocks.STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
        Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(),
        Blocks.LANTERN.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 3,
    )
    private val DEEPSLATE = Palette(
        Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
        Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(),
        Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3,
    )
    private val COPPER = Palette(
        Blocks.WAXED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
        Blocks.WAXED_CHISELED_COPPER.defaultBlockState(), Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState(),
        Blocks.LANTERN.defaultBlockState(), Blocks.WAXED_EXPOSED_COPPER.defaultBlockState(), 4,
    )
    private val QUARTZ = Palette(
        Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(),
        Blocks.SEA_LANTERN.defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(), 4,
    )
    private val BOSS = Palette(
        Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(),
        Blocks.END_ROD.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), 6,
    )

    fun floorY(floor: Int): Int = BASE_Y + (floor - 1) * FLOOR_SPACING

    fun center(slot: Int, floor: Int): BlockPos = BlockPos(slot * SLOT_SPACING, floorY(floor), 0)

    /** Onde o desafiante aparece (lado sul, olhando para o norte). */
    fun playerSpot(slot: Int, floor: Int): Vec3 = Vec3.atBottomCenterOf(center(slot, floor).offset(0, 1, 7))

    /** Onde o treinador aparece (lado norte, olhando para o sul). */
    fun trainerSpot(slot: Int, floor: Int): Vec3 = Vec3.atBottomCenterOf(center(slot, floor).offset(0, 1, -6))

    const val PLAYER_YAW = 180f
    const val TRAINER_YAW = 0f

    private fun palette(floor: Int, floors: Int, boss: Boolean): Palette {
        if (boss) return BOSS
        val progress = floor.toDouble() / floors
        return when {
            progress <= 0.25 -> STONE
            progress <= 0.5 -> DEEPSLATE
            progress <= 0.75 -> COPPER
            else -> QUARTZ
        }
    }

    fun build(level: ServerLevel, slot: Int, floor: Int, floors: Int, boss: Boolean) {
        val c = center(slot, floor)
        val p = palette(floor, floors, boss)
        val r2 = RADIUS * RADIUS + RADIUS

        for (dx in -RADIUS - 1..RADIUS + 1) {
            for (dz in -RADIUS - 1..RADIUS + 1) {
                val d2 = dx * dx + dz * dz
                // Limpa o espaço da arena
                for (dy in 1..9) set(level, c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState())
                if (d2 > r2 + 2 * RADIUS + 1) continue

                if (d2 > r2) {
                    // Parede invisível ao redor para ninguém cair no vazio
                    for (dy in 1..4) set(level, c.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState())
                    continue
                }

                val ringBand = d2 > (RADIUS - 1) * (RADIUS - 1)
                val innerBand = d2 in 26..36
                set(level, c.offset(dx, 0, dz), when {
                    ringBand -> p.ring
                    innerBand -> p.trim
                    else -> p.floor
                })
                // Base em formato de cone embaixo do piso
                if (d2 <= (RADIUS - 2) * (RADIUS - 2)) set(level, c.offset(dx, -1, dz), p.underside)
                if (d2 <= (RADIUS - 4) * (RADIUS - 4)) set(level, c.offset(dx, -2, dz), p.underside)
                if (d2 <= 9) set(level, c.offset(dx, -3, dz), p.ring)
            }
        }

        pokeBallEmblem(level, c)
        pillars(level, c, p)
        lights(level, c)
        if (boss) bossDecor(level, c)
    }

    /** Poké Bola desenhada no centro do piso, alinhada entre os dois treinadores. */
    private fun pokeBallEmblem(level: ServerLevel, c: BlockPos) {
        val red = Blocks.RED_CONCRETE.defaultBlockState()
        val white = Blocks.WHITE_CONCRETE.defaultBlockState()
        val black = Blocks.BLACK_CONCRETE.defaultBlockState()
        for (dx in -4..4) {
            for (dz in -4..4) {
                val d2 = dx * dx + dz * dz
                if (d2 > 18) continue
                val state = when {
                    d2 >= 13 -> black // contorno
                    d2 <= 1 -> white // botão
                    d2 <= 4 -> black // aro do botão
                    dz == 0 -> black // faixa do meio
                    dz < 0 -> red // metade de cima, do lado do treinador
                    else -> white
                }
                set(level, c.offset(dx, 0, dz), state)
            }
        }
    }

    private fun pillars(level: ServerLevel, c: BlockPos, p: Palette) {
        val spots = listOf(
            8 to 3, 8 to -3, -8 to 3, -8 to -3,
            3 to 8, -3 to 8, 3 to -8, -3 to -8,
        )
        for ((dx, dz) in spots) {
            set(level, c.offset(dx, 0, dz), p.trim)
            for (dy in 1..p.pillarHeight) set(level, c.offset(dx, dy, dz), p.pillar)
            set(level, c.offset(dx, p.pillarHeight + 1, dz), p.pillarTop)
        }
    }

    private fun lights(level: ServerLevel, c: BlockPos) {
        val light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
        for ((dx, dz) in listOf(0 to 0, 5 to 5, -5 to 5, 5 to -5, -5 to -5, 0 to 7, 0 to -7, 7 to 0, -7 to 0)) {
            set(level, c.offset(dx, 4, dz), light)
        }
    }

    private fun bossDecor(level: ServerLevel, c: BlockPos) {
        // Tronos dourados atrás de cada lado e cristais de ametista nos cantos
        for (dx in -1..1) {
            set(level, c.offset(dx, 1, -9), Blocks.GOLD_BLOCK.defaultBlockState())
            set(level, c.offset(dx, 2, -9), Blocks.CRYING_OBSIDIAN.defaultBlockState())
        }
        set(level, c.offset(0, 3, -9), Blocks.BEACON.defaultBlockState())
        for ((dx, dz) in listOf(6 to 6, -6 to 6, 6 to -6, -6 to -6)) {
            set(level, c.offset(dx, 1, dz), Blocks.AMETHYST_CLUSTER.defaultBlockState())
        }
    }

    private fun set(level: ServerLevel, pos: BlockPos, state: BlockState) {
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_CLIENTS)
    }
}
