package com.shifutower.command

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.mojang.authlib.GameProfile
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.shifutower.config.ConfigManager
import com.shifutower.config.ShopEntry
import com.shifutower.data.TowerStorage
import com.shifutower.network.ServerNetworking
import com.shifutower.registry.ModComponents
import com.shifutower.registry.ModItems
import com.shifutower.shop.ShopService
import com.shifutower.tower.DailyLimit
import com.shifutower.tower.Ranking
import com.shifutower.tower.TeamGenerator
import com.shifutower.tower.TowerManager
import com.shifutower.tower.TrainerSpawner
import com.shifutower.trophy.TrophyInfo
import com.shifutower.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object TowerCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, @Suppress("UNUSED_PARAMETER") context: CommandBuildContext) {
        dispatcher.register(
            Commands.literal("torre")
                .executes { open(it, 0) }
                .then(Commands.literal("ranking").executes { open(it, 1) })
                .then(Commands.literal("loja").executes { open(it, 2) })
                .then(Commands.literal("desistir").executes { ctx ->
                    val player = ctx.source.playerOrException
                    if (!TowerManager.forfeit(player)) ctx.source.sendFailure(Component.translatable("shifutower.erro.nao_na_torre"))
                    1
                }),
        )

        dispatcher.register(
            Commands.literal("shifutower")
                .requires { it.hasPermission(2) }
                .executes { open(it, 0) }
                .then(Commands.literal("recarregar").executes(::reload))
                .then(Commands.literal("recepcionista").executes(::spawnReceptionist))
                .then(Commands.literal("resetar").then(Commands.argument("alvos", GameProfileArgument.gameProfile()).executes(::resetAttempts)))
                .then(
                    Commands.literal("bp")
                        .then(Commands.literal("ver").then(Commands.argument("alvos", GameProfileArgument.gameProfile()).executes(::showBp)))
                        .then(bpChange("adicionar") { current, amount -> current + amount })
                        .then(bpChange("remover") { current, amount -> (current - amount).coerceAtLeast(0) })
                        .then(bpChange("definir") { _, amount -> amount }),
                )
                .then(
                    Commands.literal("loja")
                        .then(Commands.literal("listar").executes(::listShop))
                        .then(
                            Commands.literal("adicionar")
                                .then(
                                    Commands.argument("preco", IntegerArgumentType.integer(1))
                                        .executes { addShopItem(it, 0) }
                                        .then(Commands.argument("limite", IntegerArgumentType.integer(0)).executes { addShopItem(it, IntegerArgumentType.getInteger(it, "limite")) }),
                                ),
                        )
                        .then(Commands.literal("remover").then(Commands.argument("indice", IntegerArgumentType.integer(1)).executes(::removeShopItem))),
                )
                .then(
                    Commands.literal("ranking")
                        .then(Commands.literal("limpar").executes(::clearRanking))
                        .then(Commands.literal("remover").then(Commands.argument("alvos", GameProfileArgument.gameProfile()).executes(::removeFromRanking))),
                )
                .then(Commands.literal("trofeu").then(Commands.argument("jogador", EntityArgument.player()).executes(::giveTrophy)))
                .then(Commands.literal("testar").then(Commands.argument("andar", IntegerArgumentType.integer(1, ConfigManager.MAX_FLOORS)).executes(::testFloor)))
                .then(Commands.literal("validar").executes(::validate))
                .then(Commands.literal("arena").then(Commands.argument("andar", IntegerArgumentType.integer(1, ConfigManager.MAX_FLOORS)).executes(::previewArena)))
                .then(Commands.literal("voltar").executes { ctx ->
                    if (!TowerManager.leavePreview(ctx.source.playerOrException)) ctx.source.sendFailure(Component.translatable("shifutower.cmd.voltar_nao"))
                    1
                })
                .then(Commands.literal("encerrar").then(Commands.argument("jogador", EntityArgument.player()).executes(::forceEnd))),
        )
    }

    private fun open(ctx: CommandContext<CommandSourceStack>, tab: Int): Int {
        ServerNetworking.openTower(ctx.source.playerOrException, tab)
        return 1
    }

    private fun ok(ctx: CommandContext<CommandSourceStack>, message: Component) {
        ctx.source.sendSuccess({ message }, true)
    }

    private fun profiles(ctx: CommandContext<CommandSourceStack>): Collection<GameProfile> =
        GameProfileArgument.getGameProfiles(ctx, "alvos")

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        ConfigManager.load()
        ok(ctx, Component.translatable("shifutower.cmd.recarregado").withStyle(ChatFormatting.GREEN))
        return 1
    }

    private fun spawnReceptionist(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val config = ConfigManager.trainers.receptionist
        TrainerSpawner.spawnReceptionist(player.serverLevel(), player.position(), player.yRot, config.skin, config.name)
        ok(ctx, Component.translatable("shifutower.cmd.recepcionista").withStyle(ChatFormatting.GREEN))
        return 1
    }

    private fun resetAttempts(ctx: CommandContext<CommandSourceStack>): Int {
        val list = profiles(ctx)
        list.forEach { DailyLimit.reset(TowerStorage.get(it.id)) }
        TowerStorage.markDirty()
        ok(ctx, Component.translatable("shifutower.cmd.resetado", list.joinToString { it.name }).withStyle(ChatFormatting.GREEN))
        return list.size
    }

    private fun showBp(ctx: CommandContext<CommandSourceStack>): Int {
        profiles(ctx).forEach { profile ->
            val record = TowerStorage.get(profile.id)
            ctx.source.sendSuccess({
                Component.translatable(
                    "shifutower.cmd.bp_ver", profile.name, record.bp, record.totalBpEarned, record.clears,
                    TimeFormat.duration(record.bestTimeMs), record.bestFloor,
                ).withStyle(ChatFormatting.AQUA)
            }, false)
        }
        return 1
    }

    private fun bpChange(name: String, op: (Int, Int) -> Int) =
        Commands.literal(name).then(
            Commands.argument("alvos", GameProfileArgument.gameProfile()).then(
                Commands.argument("quantidade", IntegerArgumentType.integer(0)).executes { ctx ->
                    val amount = IntegerArgumentType.getInteger(ctx, "quantidade")
                    val list = profiles(ctx)
                    list.forEach { profile ->
                        val record = TowerStorage.get(profile.id)
                        if (record.name.isBlank()) record.name = profile.name
                        record.bp = op(record.bp, amount)
                        ctx.source.sendSuccess({ Component.translatable("shifutower.cmd.bp_novo", profile.name, record.bp).withStyle(ChatFormatting.GREEN) }, true)
                    }
                    TowerStorage.markDirty()
                    list.size
                },
            ),
        )

    private fun listShop(ctx: CommandContext<CommandSourceStack>): Int {
        val registries = ctx.source.registryAccess()
        ConfigManager.shop.entries.forEachIndexed { i, entry ->
            val stack = ShopService.stackOf(entry, registries)
            val limit = if (entry.limit > 0) " (limite ${entry.limit})" else ""
            ctx.source.sendSuccess({
                Component.literal("#${i + 1} ").withStyle(ChatFormatting.GOLD)
                    .append(ShopService.displayName(entry, stack))
                    .append(Component.literal(" x${entry.count} — ${entry.price} BP$limit").withStyle(ChatFormatting.GRAY))
            }, false)
        }
        if (ConfigManager.shop.entries.isEmpty()) ctx.source.sendSuccess({ Component.translatable("shifutower.cmd.loja_vazia") }, false)
        return 1
    }

    private fun addShopItem(ctx: CommandContext<CommandSourceStack>, limit: Int): Int {
        val player = ctx.source.playerOrException
        val stack = player.mainHandItem
        if (stack.isEmpty) {
            ctx.source.sendFailure(Component.translatable("shifutower.cmd.mao_vazia"))
            return 0
        }
        val price = IntegerArgumentType.getInteger(ctx, "preco")
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val entry = ShopEntry(
            id = "${BuiltInRegistries.ITEM.getKey(stack.item).path}_${System.currentTimeMillis().toString(36)}",
            item = itemId,
            count = stack.count,
            price = price,
            limit = limit,
            fullItem = stack.save(player.registryAccess()).toString(),
        )
        ConfigManager.shop.entries.add(entry)
        ConfigManager.saveShop()
        ok(ctx, Component.translatable("shifutower.cmd.loja_adicionado", stack.hoverName, stack.count, price, ConfigManager.shop.entries.size).withStyle(ChatFormatting.GREEN))
        return 1
    }

    private fun removeShopItem(ctx: CommandContext<CommandSourceStack>): Int {
        val index = IntegerArgumentType.getInteger(ctx, "indice") - 1
        val entries = ConfigManager.shop.entries
        if (index !in entries.indices) {
            ctx.source.sendFailure(Component.translatable("shifutower.cmd.indice_invalido", entries.size))
            return 0
        }
        val removed = entries.removeAt(index)
        ConfigManager.saveShop()
        ok(ctx, Component.translatable("shifutower.cmd.loja_removido", removed.item).withStyle(ChatFormatting.YELLOW))
        return 1
    }

    private fun clearRanking(ctx: CommandContext<CommandSourceStack>): Int {
        TowerStorage.all().values.forEach {
            it.bestTimeMs = -1
            it.bestTimeDate = ""
            it.bestTimeAt = 0
        }
        TowerStorage.markDirty()
        ok(ctx, Component.translatable("shifutower.cmd.ranking_limpo").withStyle(ChatFormatting.YELLOW))
        return 1
    }

    private fun removeFromRanking(ctx: CommandContext<CommandSourceStack>): Int {
        val list = profiles(ctx)
        list.forEach { TowerStorage.find(it.id)?.apply { bestTimeMs = -1; bestTimeDate = ""; bestTimeAt = 0 } }
        TowerStorage.markDirty()
        ok(ctx, Component.translatable("shifutower.cmd.ranking_removido", list.joinToString { it.name }).withStyle(ChatFormatting.YELLOW))
        return list.size
    }

    private fun giveTrophy(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(ctx, "jogador")
        val record = TowerStorage.get(player.uuid)
        val stack = ItemStack(ModItems.TROPHY)
        stack.set(
            ModComponents.TROPHY_INFO,
            TrophyInfo(player.gameProfile.name, record.bestTimeMs.coerceAtLeast(0), DailyLimit.todayDisplay(), Ranking.positionOf(player.uuid).coerceAtLeast(0), ConfigManager.tower.floors),
        )
        player.inventory.placeItemBackInInventory(stack)
        ok(ctx, Component.translatable("shifutower.cmd.trofeu", player.gameProfile.name).withStyle(ChatFormatting.GOLD))
        return 1
    }

    private fun testFloor(ctx: CommandContext<CommandSourceStack>): Int {
        val floor = IntegerArgumentType.getInteger(ctx, "andar")
        val tier = TeamGenerator.tierFor(floor)
        val team = TeamGenerator.generate(floor)
        ctx.source.sendSuccess({
            Component.translatable("shifutower.cmd.teste", floor, tier.name, tier.pool, TeamGenerator.levelFor(floor, tier), tier.aiSkill)
                .withStyle(ChatFormatting.GOLD)
        }, false)
        team.forEach { pokemon ->
            val moves = pokemon.moveSet.getMoves().joinToString(", ") { it.name }
            val item = if (pokemon.heldItem().isEmpty) "-" else BuiltInRegistries.ITEM.getKey(pokemon.heldItem().item).path
            val shiny = if (pokemon.shiny) " ★" else ""
            ctx.source.sendSuccess({
                Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(pokemon.getDisplayName(false).copy().withStyle(if (pokemon.species.implemented) ChatFormatting.WHITE else ChatFormatting.RED))
                    .append(Component.literal("$shiny Nv.${pokemon.level} @$item [${pokemon.ability.name}] ${pokemon.nature.name.path}").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n     $moves").withStyle(ChatFormatting.DARK_AQUA))
            }, false)
        }
        if (team.isEmpty()) ctx.source.sendFailure(Component.translatable("shifutower.erro.time_vazio", tier.pool))
        return team.size
    }

    private fun previewArena(ctx: CommandContext<CommandSourceStack>): Int {
        val floor = IntegerArgumentType.getInteger(ctx, "andar")
        val npc = TowerManager.preview(ctx.source.server, floor, ctx.source.player)
        if (npc == null) {
            ctx.source.sendFailure(Component.translatable("shifutower.erro.dimensao"))
            return 0
        }
        val team = npc.party?.toList().orEmpty().joinToString(", ") { "${it.species.name} Nv.${it.level}" }
        ok(ctx, Component.translatable("shifutower.cmd.arena", floor, npc.displayName ?: Component.literal("?"), team).withStyle(ChatFormatting.LIGHT_PURPLE))
        return 1
    }

    private fun validate(ctx: CommandContext<CommandSourceStack>): Int {
        val problems = mutableListOf<String>()
        var entries = 0
        var withoutModel = 0
        for ((group, list) in ConfigManager.pools) {
            for (entry in list) {
                entries++
                val species = PokemonSpecies.getByName(entry.species.lowercase())
                if (species == null) {
                    problems += "[$group] espécie '${entry.species}' não existe"
                    continue
                }
                if (!species.implemented) withoutModel++
                entry.moves.filter { Moves.getByName(it.lowercase()) == null }.forEach { problems += "[$group] ${entry.species}: golpe '$it'" }
                entry.abilities.filter { Abilities.get(it.lowercase()) == null }.forEach { problems += "[$group] ${entry.species}: habilidade '$it'" }
                entry.items.filter { id -> ResourceLocation.tryParse(id)?.let { !BuiltInRegistries.ITEM.containsKey(it) } ?: true }
                    .forEach { problems += "[$group] ${entry.species}: item '$it'" }
            }
        }
        val usedPools = ConfigManager.tower.tiers.map { it.pool }.filter { it !in ConfigManager.pools }
        usedPools.forEach { problems += "Faixa usa o grupo '$it', que não existe em times.json" }
        ConfigManager.shop.entries.forEach { entry ->
            if (ShopService.stackOf(entry, ctx.source.registryAccess()).isEmpty && !entry.commandsOnly) problems += "Loja: item '${entry.item}' inválido"
        }

        ctx.source.sendSuccess({
            Component.translatable("shifutower.cmd.validar", entries, ConfigManager.pools.size, withoutModel, problems.size)
                .withStyle(if (problems.isEmpty()) ChatFormatting.GREEN else ChatFormatting.YELLOW)
        }, false)
        problems.take(15).forEach { line -> ctx.source.sendSuccess({ Component.literal(" - $line").withStyle(ChatFormatting.RED) }, false) }
        if (problems.size > 15) ctx.source.sendSuccess({ Component.literal(" ... e mais ${problems.size - 15}").withStyle(ChatFormatting.RED) }, false)
        return problems.size
    }

    private fun forceEnd(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(ctx, "jogador")
        val run = TowerManager.runOf(player.uuid)
        if (run == null) {
            ctx.source.sendFailure(Component.translatable("shifutower.erro.nao_na_torre"))
            return 0
        }
        TowerManager.endRun(run, won = false, reason = "admin", refund = true)
        ok(ctx, Component.translatable("shifutower.cmd.encerrado", player.gameProfile.name).withStyle(ChatFormatting.YELLOW))
        return 1
    }
}
