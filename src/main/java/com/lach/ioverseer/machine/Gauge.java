package com.lach.ioverseer.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** An extra mod resource on a machine, e.g. Ars Nouveau Source or a Mekanism chemical tank. Label is a translation key. */
public record Gauge(String label, long amount, long max, int color, String unit) {
    public static final Codec<Gauge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("l").forGetter(Gauge::label),
            Codec.LONG.fieldOf("a").forGetter(Gauge::amount),
            Codec.LONG.fieldOf("m").forGetter(Gauge::max),
            Codec.INT.fieldOf("c").forGetter(Gauge::color),
            Codec.STRING.fieldOf("u").forGetter(Gauge::unit)
    ).apply(i, Gauge::new));

    public static final StreamCodec<FriendlyByteBuf, Gauge> STREAM_CODEC = StreamCodec.of(
            (buf, g) -> {
                buf.writeUtf(g.label, 256);
                buf.writeVarLong(g.amount);
                buf.writeVarLong(g.max);
                buf.writeInt(g.color);
                buf.writeUtf(g.unit, 16);
            },
            buf -> new Gauge(buf.readUtf(256), buf.readVarLong(), buf.readVarLong(), buf.readInt(), buf.readUtf(16)));
}
