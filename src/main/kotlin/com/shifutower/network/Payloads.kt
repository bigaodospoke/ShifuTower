package com.shifutower.network

import com.shifutower.ShifuTower
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack

private fun <T : CustomPacketPayload> type(path: String) = CustomPacketPayload.Type<T>(ShifuTower.id(path))

private fun RegistryFriendlyByteBuf.writeComponent(c: Component) = ComponentSerialization.STREAM_CODEC.encode(this, c)
private fun RegistryFriendlyByteBuf.readComponent(): Component = ComponentSerialization.STREAM_CODEC.decode(this)
private fun <T> RegistryFriendlyByteBuf.writeEntries(list: List<T>, writer: (RegistryFriendlyByteBuf, T) -> Unit) {
    writeVarInt(list.size)
    list.forEach { writer(this, it) }
}
private fun <T> RegistryFriendlyByteBuf.readEntries(reader: (RegistryFriendlyByteBuf) -> T): List<T> =
    List(readVarInt()) { reader(this) }

// ---------------------------------------------------------------- dados da GUI

data class RankView(val name: String, val timeMs: Long, val date: String, val self: Boolean)

data class ShopView(
    val index: Int,
    val stack: ItemStack,
    val name: Component,
    val description: String,
    val price: Int,
    val limit: Int,
    val bought: Int,
)

data class TierView(val name: String, val fromFloor: Int, val toFloor: Int, val teamSize: Int, val level: Int, val boss: Boolean)

data class TowerSnapshot(
    val bp: Int,
    val attemptsLeft: Int,
    val attemptsPerDay: Int,
    val msUntilReset: Long,
    val inRun: Boolean,
    val floors: Int,
    val bestFloor: Int,
    val bestTimeMs: Long,
    val clears: Int,
    val runs: Int,
    val rank: Int,
    val maxBp: Int,
    val bossName: String,
    val ranking: List<RankView>,
    val shop: List<ShopView>,
    val tiers: List<TierView>,
    val floorBp: List<Int>,
) {
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(bp)
        buf.writeVarInt(attemptsLeft)
        buf.writeVarInt(attemptsPerDay)
        buf.writeVarLong(msUntilReset)
        buf.writeBoolean(inRun)
        buf.writeVarInt(floors)
        buf.writeVarInt(bestFloor)
        buf.writeLong(bestTimeMs)
        buf.writeVarInt(clears)
        buf.writeVarInt(runs)
        buf.writeInt(rank)
        buf.writeVarInt(maxBp)
        buf.writeUtf(bossName)
        buf.writeEntries(ranking) { b, r ->
            b.writeUtf(r.name); b.writeLong(r.timeMs); b.writeUtf(r.date); b.writeBoolean(r.self)
        }
        buf.writeEntries(shop) { b, s ->
            b.writeVarInt(s.index)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s.stack)
            b.writeComponent(s.name)
            b.writeUtf(s.description)
            b.writeVarInt(s.price); b.writeVarInt(s.limit); b.writeVarInt(s.bought)
        }
        buf.writeEntries(tiers) { b, t ->
            b.writeUtf(t.name); b.writeVarInt(t.fromFloor); b.writeVarInt(t.toFloor)
            b.writeVarInt(t.teamSize); b.writeVarInt(t.level); b.writeBoolean(t.boss)
        }
        buf.writeEntries(floorBp) { b, v -> b.writeVarInt(v) }
    }

    companion object {
        fun read(buf: RegistryFriendlyByteBuf) = TowerSnapshot(
            bp = buf.readVarInt(),
            attemptsLeft = buf.readVarInt(),
            attemptsPerDay = buf.readVarInt(),
            msUntilReset = buf.readVarLong(),
            inRun = buf.readBoolean(),
            floors = buf.readVarInt(),
            bestFloor = buf.readVarInt(),
            bestTimeMs = buf.readLong(),
            clears = buf.readVarInt(),
            runs = buf.readVarInt(),
            rank = buf.readInt(),
            maxBp = buf.readVarInt(),
            bossName = buf.readUtf(),
            ranking = buf.readEntries { RankView(it.readUtf(), it.readLong(), it.readUtf(), it.readBoolean()) },
            shop = buf.readEntries {
                ShopView(
                    it.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(it), it.readComponent(),
                    it.readUtf(), it.readVarInt(), it.readVarInt(), it.readVarInt(),
                )
            },
            tiers = buf.readEntries { TierView(it.readUtf(), it.readVarInt(), it.readVarInt(), it.readVarInt(), it.readVarInt(), it.readBoolean()) },
            floorBp = buf.readEntries { it.readVarInt() },
        )
    }
}

// ---------------------------------------------------------------- servidor -> cliente

/** Abre (ou atualiza) a tela da torre. */
data class OpenTowerPayload(val snapshot: TowerSnapshot, val tab: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<OpenTowerPayload>("abrir_torre")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenTowerPayload> = StreamCodec.of(
            { buf, p -> p.snapshot.write(buf); buf.writeVarInt(p.tab) },
            { buf -> OpenTowerPayload(TowerSnapshot.read(buf), buf.readVarInt()) },
        )
    }
}

/** Estado do HUD durante o desafio (andar, cronômetro, BP). */
data class RunHudPayload(
    val active: Boolean,
    val floor: Int,
    val floors: Int,
    val elapsedMs: Long,
    val bpEarned: Int,
    val boss: Boolean,
    val trainerName: String,
    val tierName: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<RunHudPayload>("hud")
        val INACTIVE = RunHudPayload(false, 0, 0, 0, 0, false, "", "")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RunHudPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.active); buf.writeVarInt(p.floor); buf.writeVarInt(p.floors)
                buf.writeVarLong(p.elapsedMs); buf.writeVarInt(p.bpEarned); buf.writeBoolean(p.boss)
                buf.writeUtf(p.trainerName); buf.writeUtf(p.tierName)
            },
            { buf ->
                RunHudPayload(
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readUtf(),
                )
            },
        )
    }
}

/** Balão de fala em cima de um NPC. */
data class SpeechPayload(
    val entityId: Int,
    val speaker: Component,
    val text: Component,
    val ticks: Int,
    val style: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val STYLE_TRAINER = 0
        const val STYLE_BOSS = 1
        const val STYLE_RECEPTIONIST = 2

        val TYPE = type<SpeechPayload>("fala")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SpeechPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.entityId); buf.writeComponent(p.speaker); buf.writeComponent(p.text)
                buf.writeVarInt(p.ticks); buf.writeVarInt(p.style)
            },
            { buf -> SpeechPayload(buf.readVarInt(), buf.readComponent(), buf.readComponent(), buf.readVarInt(), buf.readVarInt()) },
        )
    }
}

/** Resultado do desafio (vitória ou derrota). */
data class RunResultPayload(
    val won: Boolean,
    val floorReached: Int,
    val floors: Int,
    val timeMs: Long,
    val bpEarned: Int,
    val totalBp: Int,
    val rank: Int,
    val newRecord: Boolean,
    val trophy: Boolean,
    val reason: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<RunResultPayload>("resultado")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RunResultPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.won); buf.writeVarInt(p.floorReached); buf.writeVarInt(p.floors)
                buf.writeVarLong(p.timeMs); buf.writeVarInt(p.bpEarned); buf.writeVarInt(p.totalBp)
                buf.writeInt(p.rank); buf.writeBoolean(p.newRecord); buf.writeBoolean(p.trophy); buf.writeUtf(p.reason)
            },
            { buf ->
                RunResultPayload(
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                )
            },
        )
    }
}

// ---------------------------------------------------------------- cliente -> servidor

/** Ações vindas da GUI. */
data class TowerActionPayload(val action: Int, val arg: Int, val arg2: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val START = 0
        const val FORFEIT = 1
        const val BUY = 2
        const val REFRESH = 3

        val TYPE = type<TowerActionPayload>("acao")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TowerActionPayload> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.action); buf.writeVarInt(p.arg); buf.writeVarInt(p.arg2) },
            { buf -> TowerActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) },
        )
    }
}
