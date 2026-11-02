package com.example.multiaction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
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
            if (player == null) return;

            int mult = ActionHandler.getMultiplier(player);
            if (mult <= 0) return;

            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
            
            player.server.getPlayerList().broadcastSystemMessage(
                Component.literal("§e" + player.getName().getString() + " §7совершил газенваген! (" + mult + "x)"), false
            );

            AreaEffectCloud cloud = new AreaEffectCloud(player.level(), player.getX(), player.getY(), player.getZ());
            cloud.setRadius(2.5f * mult);
            cloud.setDuration(100 * mult);
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100 * mult, 1));
            player.level().addFreshEntity(cloud);
        });
        ctx.get().setPacketHandled(true);
    }
}
