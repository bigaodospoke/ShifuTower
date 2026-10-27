package com.shifutower.trophy

import com.shifutower.registry.ModComponents
import com.shifutower.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.Block

class TrophyItem(block: Block, properties: Properties) : BlockItem(block, properties) {

    override fun getName(stack: ItemStack): Component =
        Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)

    override fun isFoil(stack: ItemStack): Boolean = false

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        val info = stack.get(ModComponents.TROPHY_INFO)
        tooltip.add(Component.translatable("item.shifutower.trofeu.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
        if (info == null) return
        tooltip.add(Component.empty())
        tooltip.add(
            Component.translatable("item.shifutower.trofeu.dono", Component.literal(info.owner).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY),
        )
        tooltip.add(
            Component.translatable("item.shifutower.trofeu.tempo", Component.literal(TimeFormat.duration(info.timeMs)).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY),
        )
        if (info.floors > 0) {
            tooltip.add(
                Component.translatable("item.shifutower.trofeu.andares", Component.literal(info.floors.toString()).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY),
            )
        }
        if (info.rank > 0) {
            tooltip.add(
                Component.translatable("item.shifutower.trofeu.posicao", Component.literal("#${info.rank}").withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY),
            )
        }
        tooltip.add(
            Component.translatable("item.shifutower.trofeu.data", Component.literal(info.date).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY),
        )
    }
}
