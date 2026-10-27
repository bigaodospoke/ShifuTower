package com.shifutower.tower

import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.shifutower.ShifuTower
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/** Cria os NPCs da torre usando o sistema de NPC nativo do Cobblemon. */
object TrainerSpawner {
    const val TAG_TRAINER = "shifutower.treinador"
    const val TAG_RECEPTIONIST = "shifutower.recepcionista"

    private val TRAINER_CLASS = ShifuTower.id("treinador_torre")
    private val BOSS_CLASS = ShifuTower.id("mestre_torre")
    private val RECEPTIONIST_CLASS = ShifuTower.id("recepcionista")

    fun isTrainer(entity: Entity) = entity.tags.contains(TAG_TRAINER)

    fun isReceptionist(entity: Entity) = entity.tags.contains(TAG_RECEPTIONIST)

    fun spawnTrainer(
        level: ServerLevel,
        pos: Vec3,
        yaw: Float,
        skin: String,
        name: String,
        team: List<Pokemon>,
        npcLevel: Int,
        skill: Int,
        boss: Boolean,
    ): NPCEntity {
        val npc = create(level, if (boss) BOSS_CLASS else TRAINER_CLASS, pos, yaw, npcLevel)
        applySkin(npc, skin)
        npc.customName = Component.literal(name).withStyle(if (boss) ChatFormatting.GOLD else ChatFormatting.YELLOW)
        val party = NPCPartyStore(npc)
        team.forEach { party.add(it) }
        npc.party = party
        npc.skill = skill.coerceIn(0, 5)
        npc.addTag(TAG_TRAINER)
        level.addFreshEntity(npc)
        return npc
    }

    fun spawnReceptionist(level: ServerLevel, pos: Vec3, yaw: Float, skin: String, name: String): NPCEntity {
        val npc = create(level, RECEPTIONIST_CLASS, pos, yaw, 1)
        applySkin(npc, skin)
        npc.customName = Component.literal(name).withStyle(ChatFormatting.AQUA)
        npc.addTag(TAG_RECEPTIONIST)
        level.addFreshEntity(npc)
        return npc
    }

    private fun create(level: ServerLevel, classId: ResourceLocation, pos: Vec3, yaw: Float, npcLevel: Int): NPCEntity {
        val npc = NPCEntity(level)
        val npcClass = NPCClasses.getByIdentifier(classId)
        if (npcClass == null) {
            ShifuTower.LOGGER.warn("Classe de NPC {} não encontrada (datapack do mod não carregou?) — usando uma aleatória", classId)
        }
        npc.npc = npcClass ?: NPCClasses.random()
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0f)
        npc.yHeadRot = yaw
        npc.yBodyRot = yaw
        npc.initialize(npcLevel.coerceAtLeast(1))
        return npc
    }

    /** "jogador:<Nick>" carrega a skin de um jogador; qualquer outro valor é um resolver de NPC (ex.: shifutower:veterana). */
    private fun applySkin(npc: NPCEntity, skin: String) {
        if (skin.isBlank()) return
        if (skin.startsWith("jogador:", ignoreCase = true)) {
            runCatching { npc.loadTextureFromGameProfileName(skin.substringAfter(':')) }
                .onFailure { ShifuTower.LOGGER.warn("Não consegui carregar a skin de {}: {}", skin, it.message) }
            return
        }
        ResourceLocation.tryParse(skin)?.let { npc.forcedResourceIdentifier = it }
    }
}
