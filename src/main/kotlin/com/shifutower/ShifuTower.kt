package com.shifutower

import com.shifutower.command.TowerCommands
import com.shifutower.config.ConfigManager
import com.shifutower.data.TowerStorage
import com.shifutower.network.ServerNetworking
import com.shifutower.registry.ModBlockEntities
import com.shifutower.registry.ModBlocks
import com.shifutower.registry.ModComponents
import com.shifutower.registry.ModItems
import com.shifutower.tower.TowerEvents
import com.shifutower.tower.TowerManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ShifuTower : ModInitializer {
    const val MOD_ID = "shifutower"
    val LOGGER: Logger = LoggerFactory.getLogger("ShifuTower")

    /** Dimensão onde as arenas da torre são geradas. */
    val TOWER_DIMENSION: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, id("torre"))

    var server: MinecraftServer? = null
        private set

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    override fun onInitialize() {
        ModComponents.register()
        ModBlocks.register()
        ModBlockEntities.register()
        ModItems.register()
        ServerNetworking.register()
        ConfigManager.load()

        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            TowerCommands.register(dispatcher, registryAccess)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            TowerStorage.load(srv)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { srv ->
            TowerManager.shutdown(srv)
            TowerStorage.save()
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
        }
        ServerTickEvents.END_SERVER_TICK.register { srv ->
            TowerManager.tick(srv)
            TowerStorage.tick()
        }

        TowerEvents.register()
        LOGGER.info("ShifuTower carregado — boa sorte na subida!")
    }
}
