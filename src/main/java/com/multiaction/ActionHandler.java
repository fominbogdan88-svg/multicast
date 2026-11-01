package com.multiaction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Random;

public class ActionHandler {
    private static final Random RNG = new Random();
    private static boolean busy = false;

    private int roll() {
        int r = RNG.nextInt(100);
        if (r < 2) return 0;   // 2% провал
        if (r < 60) return 1;  // 58% обычно
        if (r < 85) return 2;  // 25% x2
        if (r < 95) return 4;  // 10% x4
        return 16;             // 5% x16
    }

    private void notify(Player p, String a, int n) {
        if (p == null || p.level().isClientSide() || n == 1) return;
        ChatFormatting c = (n == 0) ? ChatFormatting.RED : ChatFormatting.AQUA;
        String t = (n == 0) ? "✘ " + a + " ОТМЕНЕНО" : "⚡ " + a + " x" + n;
        p.sendSystemMessage(Component.literal(t).withStyle(c));
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (busy || e.getPlayer().isCreative()) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); notify(e.getPlayer(), "Действие", 0); return; }
        if (n > 1) {
            notify(e.getPlayer(), "Блок", n);
            // Даем дополнительный опыт в качестве бонуса
            e.setExpToDrop(e.getExpToDrop() * n);
        }
    }

    @SubscribeEvent
    public void onXP(PlayerXpEvent.XpChange e) {
        if (busy) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); notify(e.getEntity(), "Опыт", 0); return; }
        if (n > 1) {
            busy = true;
            e.setAmount(e.getAmount() * n);
            notify(e.getEntity(), "Опыт", n);
            busy = false;
        }
    }
}
