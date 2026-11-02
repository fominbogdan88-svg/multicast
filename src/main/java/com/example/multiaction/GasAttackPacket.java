package com.example.multiaction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GasAttackPacket {

    public GasAttackPacket() {}

    public static void encode(GasAttackPacket msg, FriendlyByteBuf buf) {}

    public static GasAttackPacket decode(FriendlyByteBuf buf) {
        return new GasAttackPacket();
    }

    public static void handle(GasAttackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ActionHandler.performGasAttack(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
