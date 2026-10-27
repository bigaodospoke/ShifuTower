package com.shifutower.trophy

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/** Dados gravados no troféu: quem conquistou, em quanto tempo e quando. */
data class TrophyInfo(
    val owner: String,
    val timeMs: Long,
    val date: String,
    val rank: Int,
    val floors: Int,
) {
    companion object {
        val CODEC: Codec<TrophyInfo> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("owner").forGetter(TrophyInfo::owner),
                Codec.LONG.fieldOf("time_ms").forGetter(TrophyInfo::timeMs),
                Codec.STRING.fieldOf("date").forGetter(TrophyInfo::date),
                Codec.INT.optionalFieldOf("rank", 0).forGetter(TrophyInfo::rank),
                Codec.INT.optionalFieldOf("floors", 0).forGetter(TrophyInfo::floors),
            ).apply(i, ::TrophyInfo)
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, TrophyInfo> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TrophyInfo::owner,
            ByteBufCodecs.VAR_LONG, TrophyInfo::timeMs,
            ByteBufCodecs.STRING_UTF8, TrophyInfo::date,
            ByteBufCodecs.VAR_INT, TrophyInfo::rank,
            ByteBufCodecs.VAR_INT, TrophyInfo::floors,
            ::TrophyInfo,
        )
    }
}
