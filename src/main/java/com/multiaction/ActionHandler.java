package com.multiaction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Random;

public class ActionHandler {
    private static final Random RNG = new Random();
    private static boolean busy = false;

    private static final int[] W = {
        20, 550, 200, 100, 50, 30, 15, 10, 7, 5, 4, 3, 2, 2, 1, 1, 10
    };

    private static int roll() {
        int r = RNG.nextInt(1000), s = 0;
        for (int i = 0; i < W.length; i++) { s += W[i]; if (r < s) return i; }
        return 1;
    }

    private static void msg(Player p, String a, int n) {
        if (p.level().isClientSide()) return;
        Component m;
        if (n == 0) m = Component.literal("✘ " + a + " провал!").withStyle(ChatFormatting.RED);
        else if (n >= 10) m = Component.literal("★ ДЖЕКПОТ " + a + " x" + n).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        else if (n >= 5) m = Component.literal("✦ Супер " + a + " x" + n).withStyle(ChatFormatting.LIGHT_PURPLE);
        else if (n > 1) m = Component.literal("⚡ " + a + " x" + n).withStyle(ChatFormatting.AQUA);
        else return;
        p.sendSystemMessage(m);
    }

    private static void drop(ServerLevel l, double x, double y, double z, ItemStack s) {
        ItemEntity e = new ItemEntity(l, x, y, z, s.copy());
        e.setDefaultPickUpDelay();
        l.addFreshEntity(e);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        Player p = e.getPlayer();
        if (p == null || busy || p.isCreative()) return;
        if (!(e.getLevel() instanceof ServerLevel l)) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); msg(p, "Блок", 0); return; }
        if (n == 1) return;
        BlockPos pos = e.getPos();
        BlockState st = l.getBlockState(pos);
        List<ItemStack> d = st.getDrops(new LootParams.Builder(l)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, p.getMainHandItem())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, p)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, l.getBlockEntity(pos)));
        for (int i = 0; i < n - 1; i++)
            for (ItemStack s : d) drop(l, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, s);
        msg(p, "Блок", n);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (busy) return;
        if (!(e.getSource().getEntity() instanceof Player p)) return;
        if (p.level().isClientSide()) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); msg(p, "Атака", 0); return; }
        if (n == 1) return;
        LivingEntity t = e.getEntity();
        float d = e.getAmount();
        busy = true;
        try {
            for (int i = 0; i < n - 1; i++) {
                t.invulnerableTime = 0;
                t.hurt(e.getSource(), d);
                if (!t.isAlive()) break;
            }
        } finally { busy = false; }
        msg(p, "Атака", n);
    }

    @SubscribeEvent
    public void onFish(ItemFishedEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); msg(p, "Рыбалка", 0); return; }
        if (n == 1) return;
        if (p.level() instanceof ServerLevel l)
            for (int i = 0; i < n - 1; i++)
                for (ItemStack s : e.getDrops()) drop(l, p.getX(), p.getY() + .5, p.getZ(), s);
        msg(p, "Рыбалка", n);
    }

    @SubscribeEvent
    public void onXP(PlayerXpEvent.XpChange e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        int n = roll();
        if (n == 0) { e.setCanceled(true); msg(p, "Опыт", 0); return; }
        if (n == 1) return;
        e.setAmount(e.getAmount() * n);
        msg(p, "Опыт", n);
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        int n = roll();
        ItemStack r = e.getCrafting();
        if (n == 0) { r.setCount(0); msg(p, "Крафт", 0); return; }
        if (n == 1) return;
        if (p.level() instanceof ServerLevel l) {
            int t = r.getCount() * (n - 1), mx = r.getMaxStackSize();
            while (t > 0) { ItemStack s = r.copy(); s.setCount(Math.min(t, mx)); drop(l, p.getX(), p.getY() + .5, p.getZ(), s); t -= mx; }
        }
        msg(p, "Крафт", n);
    }

    @SubscribeEvent
    public void onSmelt(PlayerEvent.ItemSmeltedEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        int n = roll();
        ItemStack r = e.getSmelting();
        if (n == 0) { r.setCount(0); msg(p, "Плавка", 0); return; }
        if (n == 1) return;
        if (p.level() instanceof ServerLevel l)
            for (int i = 0; i < n - 1; i++) drop(l, p.getX(), p.getY() + .5, p.getZ(), r);
        msg(p, "Плавка", n);
    }

    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.level().isClientSide()) return;
        ItemStack it = e.getItem();
        if (!it.isEdible()) return;
        FoodProperties f = it.getItem().getFoodProperties();
        if (f == null) return;
        int n = roll();
        if (n == 0) { p.getFoodData().setFoodLevel(Math.max(0, p.getFoodData().getFoodLevel() - f.getNutrition())); msg(p, "Еда", 0); return; }
        if (n == 1) return;
        for (int i = 0; i < n - 1; i++) p.getFoodData().eat(f.getNutrition(), f.getSaturationModifier());
        msg(p, "Еда", n);
    }
}
