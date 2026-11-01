package com.multiaction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = "multiaction")
public class ActionHandler {
    private static final Random RNG = new Random();
    private static boolean busy = false;

    // Шансы: 0 = 3%, 1 = 47%, 2-15 = 49%, 16 = 1%
    private static final int[] WEIGHTS = {
        30,  // 0  (3.0%) - Провал
        470, // 1  (47.0%) - Обычное
        180, // 2  (18.0%)
        100, // 3  (10.0%)
        60,  // 4  (6.0%)
        40,  // 5  (4.0%)
        30,  // 6  (3.0%)
        20,  // 7  (2.0%)
        15,  // 8  (1.5%)
        15,  // 9  (1.5%)
        10,  // 10 (1.0%)
        8,   // 11 (0.8%)
        6,   // 12 (0.6%)
        3,   // 13 (0.3%)
        2,   // 14 (0.2%)
        1,   // 15 (0.1%)
        10   // 16 (1.0%) - ДЖЕКПОТ
    };

    private static int roll() {
        int r = RNG.nextInt(1000);
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += WEIGHTS[i];
            if (r < sum) return i;
        }
        return 1;
    }

    private static void notify(Player p, String action, int n) {
        if (p == null || p.level().isClientSide()) return;
        
        if (n == 0) {
            p.sendSystemMessage(Component.literal("✘ " + action + " - ОТМЕНЕНО!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (n >= 10) {
            p.sendSystemMessage(Component.literal("★ МЕГА ДЖЕКПОТ! " + action + " x" + n).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (n > 1) {
            p.sendSystemMessage(Component.literal("⚡ " + action + " x" + n).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }

    private static void spawnItem(ServerLevel level, double x, double y, double z, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(level, x, y, z, stack.copy());
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // 1. ЛОМАНИЕ БЛОКОВ (Умножение предметов)
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (player == null || busy || player.isCreative()) return;
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        int n = roll();
        if (n == 0) {
            e.setCanceled(true);
            notify(player, "Ломание блока", 0);
            return;
        }
        if (n == 1) return;

        BlockPos pos = e.getPos();
        BlockState state = e.getState();

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos));

        List<ItemStack> drops = new ArrayList<>(state.getDrops(builder));
        
        // Защита: если дроп пустой (например, копаем землю рукой), создаем предмет блока насильно!
        if (drops.isEmpty() && !state.is(Blocks.AIR)) {
            ItemStack defaultDrop = new ItemStack(state.getBlock().asItem());
            if (!defaultDrop.isEmpty()) {
                drops.add(defaultDrop);
            }
        }

        // Спавним дополнительные копии дропа
        for (int i = 0; i < n - 1; i++) {
            for (ItemStack drop : drops) {
                spawnItem(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            }
        }
        notify(player, "Дроп блоков", n);
    }

    // 2. УСТАНОВКА БЛОКОВ (Застройка)
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (busy) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || player.isCreative()) return;

        int n = roll();
        if (n == 0) {
            e.setCanceled(true);
            notify(player, "Установка блока", 0);
            return;
        }
        if (n > 1 && e.getLevel() instanceof ServerLevel level) {
            BlockPos pos = e.getPos();
            BlockState state = e.getPlacedBlock();
            BlockPos[] offsets = {
                pos.above(), pos.north(), pos.south(), pos.east(), pos.west(), pos.below()
            };
            int placed = 0;
            busy = true;
            try {
                for (BlockPos offset : offsets) {
                    if (placed >= n - 1) break;
                    if (level.getBlockState(offset).isAir()) {
                        level.setBlock(offset, state, 3);
                        placed++;
                    }
                }
            } finally {
                busy = false;
            }
            if (placed > 0) notify(player, "Размещение блоков", placed + 1);
        }
    }

    // 3. АТАКА (Множитель урона)
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (busy) return;
        if (!(e.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        int n = roll();
        if (n == 0) {
            e.setCanceled(true);
            notify(player, "Атака", 0);
            return;
        }
        if (n > 1) {
            e.setAmount(e.getAmount() * n);
            notify(player, "Урон", n);
        }
    }

    // 4. КРАФТ
    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent e) {
        Player player = e.getEntity();
        if (player.level().isClientSide()) return;

        int n = roll();
        ItemStack result = e.getCrafting();
        if (n == 0) {
            result.setCount(0);
            notify(player, "Крафт", 0);
            return;
        }
        if (n > 1 && player.level() instanceof ServerLevel level) {
            int extraCount = result.getCount() * (n - 1);
            int maxStack = result.getMaxStackSize();
            while (extraCount > 0) {
                ItemStack stack = result.copy();
                stack.setCount(Math.min(extraCount, maxStack));
                spawnItem(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
                extraCount -= maxStack;
            }
            notify(player, "Крафт", n);
        }
    }

    // 5. ЕДА
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack item = e.getItem();
        if (!item.isEdible()) return;

        FoodProperties food = item.getItem().getFoodProperties(item, player);
        if (food == null) return;

        int n = roll();
        if (n == 0) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - food.getNutrition()));
            notify(player, "Еда", 0);
            return;
        }
        if (n > 1) {
            for (int i = 0; i < n - 1; i++) {
                player.getFoodData().eat(food.getNutrition(), food.getSaturationModifier());
            }
            notify(player, "Еда", n);
        }
    }

    // 6. ОПЫТ
    @SubscribeEvent
    public static void onXP(PlayerXpEvent.XpChange e) {
        if (busy) return;
        Player player = e.getEntity();
        if (player.level().isClientSide()) return;

        int n = roll();
        if (n == 0) {
            e.setCanceled(true);
            notify(player, "Опыт", 0);
            return;
        }
        if (n > 1) {
            busy = true;
            e.setAmount(e.getAmount() * n);
            notify(player, "Опыт", n);
            busy = false;
        }
    }

    // 7. РЫБАЛКА
    @SubscribeEvent
    public static void onFish(ItemFishedEvent e) {
        Player player = e.getEntity();
        if (player.level().isClientSide()) return;

        int n = roll();
        if (n == 0) {
            e.setCanceled(true);
            notify(player, "Рыбалка", 0);
            return;
        }
        if (n > 1 && player.level() instanceof ServerLevel level) {
            for (int i = 0; i < n - 1; i++) {
                for (ItemStack drop : e.getDrops()) {
                    spawnItem(level, player.getX(), player.getY() + 0.5, player.getZ(), drop);
                }
            }
            notify(player, "Рыбалка", n);
        }
    }
}
