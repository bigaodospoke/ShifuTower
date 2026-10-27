package com.shifutower.shop

import com.shifutower.ShifuTower
import com.shifutower.config.ConfigManager
import com.shifutower.config.ShopEntry
import com.shifutower.data.PlayerRecord
import com.shifutower.data.TowerStorage
import com.shifutower.network.ShopView
import net.minecraft.ChatFormatting
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack

/** Loja da torre: troca Battle Points por itens configuráveis. */
object ShopService {

    fun stackOf(entry: ShopEntry, registries: HolderLookup.Provider): ItemStack {
        if (entry.fullItem.isNotBlank()) {
            return try {
                ItemStack.parse(registries, TagParser.parseTag(entry.fullItem)).orElse(ItemStack.EMPTY)
            } catch (e: Exception) {
                ShifuTower.LOGGER.warn("Item completo inválido na loja ({}): {}", entry.id, e.message)
                ItemStack.EMPTY
            }
        }
        val id = ResourceLocation.tryParse(entry.item) ?: return ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return ItemStack.EMPTY
        return ItemStack(item, entry.count.coerceAtLeast(1))
    }

    fun displayName(entry: ShopEntry, stack: ItemStack): Component =
        if (entry.displayName.isNotBlank()) {
            Component.literal(entry.displayName.replace('&', '§'))
        } else if (stack.isEmpty) {
            Component.literal(entry.item).withStyle(ChatFormatting.RED)
        } else {
            stack.hoverName
        }

    fun views(player: ServerPlayer, record: PlayerRecord): List<ShopView> {
        val registries = player.registryAccess()
        return ConfigManager.shop.entries.mapIndexed { index, entry ->
            val stack = stackOf(entry, registries)
            val shown = if (stack.isEmpty && entry.commandsOnly) ItemStack(net.minecraft.world.item.Items.PAPER) else stack
            ShopView(
                index = index,
                stack = shown.copyWithCount(shown.count.coerceIn(1, 99)),
                name = displayName(entry, stack),
                description = entry.description.replace('&', '§'),
                price = entry.price,
                limit = entry.limit,
                bought = record.purchases[entry.id] ?: 0,
            )
        }
    }

    fun buy(player: ServerPlayer, index: Int, expectedPrice: Int) {
        val entry = ConfigManager.shop.entries.getOrNull(index)
        if (entry == null || entry.price != expectedPrice) {
            fail(player, Component.translatable("shifutower.loja.mudou"))
            return
        }
        val record = TowerStorage.get(player.uuid)
        if (record.bp < entry.price) {
            fail(player, Component.translatable("shifutower.loja.sem_bp", entry.price - record.bp))
            return
        }
        val bought = record.purchases[entry.id] ?: 0
        if (entry.limit > 0 && bought >= entry.limit) {
            fail(player, Component.translatable("shifutower.loja.limite"))
            return
        }
        val stack = stackOf(entry, player.registryAccess())
        if (stack.isEmpty && !entry.commandsOnly && entry.commands.isEmpty()) {
            fail(player, Component.translatable("shifutower.loja.item_invalido"))
            return
        }

        record.bp -= entry.price
        record.purchases[entry.id] = bought + 1
        TowerStorage.markDirty()

        if (!entry.commandsOnly && !stack.isEmpty) give(player, stack)
        if (entry.commands.isNotEmpty()) {
            val server = player.server
            val source = server.createCommandSourceStack().withSuppressedOutput()
            entry.commands.forEach { command ->
                server.commands.performPrefixedCommand(source, command.replace("{jogador}", player.gameProfile.name).removePrefix("/"))
            }
        }
        player.sendSystemMessage(
            Component.translatable("shifutower.loja.comprou", displayName(entry, stack), entry.price, record.bp)
                .withStyle(ChatFormatting.GREEN),
        )
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f)
    }

    /** Entrega respeitando o tamanho máximo de pilha (ex.: 100 balas raras = 2 pilhas). */
    private fun give(player: ServerPlayer, stack: ItemStack) {
        var remaining = stack.count
        val max = stack.maxStackSize.coerceAtLeast(1)
        while (remaining > 0) {
            val amount = minOf(remaining, max)
            player.inventory.placeItemBackInInventory(stack.copyWithCount(amount))
            remaining -= amount
        }
    }

    private fun fail(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED))
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1f)
    }
}
