package com.example.startercustomizer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class StarterPackets {
    public static final int MAX_PRESET_LENGTH = 1024;

    private StarterPackets() {
    }

    public record Claim(String preset) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(id("claim"));
        public static final StreamCodec<ByteBuf, Claim> CODEC =
                ByteBufCodecs.stringUtf8(MAX_PRESET_LENGTH).map(Claim::new, Claim::preset);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    public record Result(String error) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(id("claim_result"));
        public static final StreamCodec<ByteBuf, Result> CODEC =
                ByteBufCodecs.stringUtf8(2048).map(Result::new, Result::error);

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StarterCustomizer.MOD_ID, path);
    }
}
