package com.shifutower.trophy

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Troféu de campeão da Torre Shifu: um bloco decorativo com modelo 3D próprio. */
class TrophyBlock(properties: Properties) : BaseEntityBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (state.getValue(FACING).axis) {
            Direction.Axis.X -> SHAPE_X
            else -> SHAPE_Z
        }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = TrophyBlockEntity(pos, state)

    companion object {
        val CODEC: MapCodec<TrophyBlock> = simpleCodec(::TrophyBlock)
        val FACING = BlockStateProperties.HORIZONTAL_FACING

        private val BODY: VoxelShape = Shapes.or(
            box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
            box(6.5, 3.0, 6.5, 9.5, 7.25, 9.5),
            box(4.0, 7.25, 4.0, 12.0, 13.5, 12.0),
        )

        /** Alças apontando para leste/oeste (troféu virado para norte/sul). */
        private val SHAPE_Z: VoxelShape = Shapes.or(BODY, box(2.25, 8.75, 7.5, 13.75, 12.25, 8.5))

        /** Alças apontando para norte/sul (troféu virado para leste/oeste). */
        private val SHAPE_X: VoxelShape = Shapes.or(BODY, box(7.5, 8.75, 2.25, 8.5, 12.25, 13.75))
    }
}
