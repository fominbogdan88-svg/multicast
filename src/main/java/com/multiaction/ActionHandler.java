package com.example.multiaction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = MultiActionMod.MODID)
public class ActionHandler {

    private static final Map<UUID, Integer> FORCED_MULTIPLIERS = new HashMap<>();
    private static final Random RANDOM = new Random();

    // Веса от 0x до 16x
    private static final int[] MULTIPLIER_WEIGHTS = {
        30, 470, 180, 100, 60, 40, 30, 20, 15, 15, 10, 8, 6, 3, 2, 1, 10
    };

    public static int getRandomMultiplier(Player player) {
        if (player != null && FORCED_MULTIPLIERS.containsKey(player.getUUID())) {
            return FORCED_MULTIPLIERS.get(player.getUUID());
        }

        int luckBonus = 0;
        if (player != null && player.hasEffect(MobEffects.LUCK)) {
            MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
            if (luck != null) {
                luckBonus = (luck.getAmplifier() + 1) * 150;
            }
        }

        int totalWeight = 0;
        for (int w : MULTIPLIER_WEIGHTS) totalWeight += w;

        int roll = RANDOM.nextInt(totalWeight);
        if (luckBonus > 0) {
            roll = Math.min(totalWeight - 1, roll + luckBonus);
        }

        int current = 0;
        for (int i = 0; i < MULTIPLIER_WEIGHTS.length; i++) {
            current += MULTIPLIER_WEIGHTS[i];
            if (roll < current) return i;
        }

        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("multiaction")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(-1, 16))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        int level = IntegerArgumentType.getInteger(context, "level");
                        if (level == -1) {
                            FORCED_MULTIPLIERS.remove(player.getUUID());
                            context.getSource().sendSuccess(() -> Component.literal("§a[MultiAction] Мультипликатор сброшен на рандом."), false);
                        } else {
                            FORCED_MULTIPLIERS.put(player.getUUID(), level);
                            context.getSource().sendSuccess(() -> Component.literal("§a[MultiAction] Установлен мультипликатор: " + level + "x"), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("luckpotion")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ItemStack potionStack = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LUCK);
                        player.getInventory().add(potionStack);
                        context.getSource().sendSuccess(() -> Component.literal("§aВы получили зелье Удачи!"), false);
                        return 1;
                    }))
        );
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        Player player = level.getNearestPlayer(event.getExplosion().getPosition().x, event.getExplosion().getPosition().y, event.getExplosion().getPosition().z, 16.0, false);
        int multiplier = getRandomMultiplier(player);

        if (multiplier == 0) {
            event.setCanceled(true);
            return;
        }

        for (int i = 1; i < multiplier; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 4.0;
            double oz = (RANDOM.nextDouble() - 0.5) * 4.0;
            level.explode(null, event.getExplosion().getPosition().x + ox, event.getExplosion().getPosition().y, event.getExplosion().getPosition().z + oz, 3.0F, Level.ExplosionInteraction.TNT);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        int multiplier = getRandomMultiplier(player);
        if (multiplier == 0) {
            event.setCanceled(true);
            return;
        }

        for (int i = 1; i < multiplier; i++) {
            Arrow arrow = new Arrow(player.level(), player);
            arrow.shootFromRotation(player, player.getXRot() + (RANDOM.nextFloat() - 0.5F) * 12.0F, player.getYRot() + (RANDOM.nextFloat() - 0.5F) * 12.0F, 0.0F, event.getCharge() * 0.1F, 1.0F);
            player.level().addFreshEntity(arrow);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getLevel().isClientSide()) return;

        int multiplier = getRandomMultiplier(player);
        if (multiplier == 0) {
            event.setCanceled(true);
            return;
        }

        Level level = (Level) event.getLevel();
        BlockPos startPos = event.getPos();
        BlockState targetState = event.getState();

        int extraBlocksBroken = 0;
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        Set<BlockPos> visited = new HashSet<>();
        visited.add(startPos);

        while (!queue.isEmpty() && extraBlocksBroken < multiplier - 1) {
            BlockPos current = queue.poll();
            for (BlockPos neighbor : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                BlockPos copyPos = neighbor.immutable();
                if (!visited.contains(copyPos) && level.getBlockState(copyPos).is(targetState.getBlock())) {
                    visited.add(copyPos);
                    level.destroyBlock(copyPos, true, player);
                    extraBlocksBroken++;
                    queue.add(copyPos);
                    if (extraBlocksBroken >= multiplier - 1) break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPotionApplicable(MobEffectEvent.Applicable event) {
        int multiplier = getRandomMultiplier(null);
        if (multiplier == 0) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }

        MobEffectInstance effect = event.getEffectInstance();
        int newDuration = effect.getDuration() * multiplier;
        int newAmp = Math.min(4, effect.getAmplifier() + (multiplier > 8 ? 2 : (multiplier > 3 ? 1 : 0)));

        MobEffectInstance updated = new MobEffectInstance(effect.getEffect(), newDuration, newAmp, effect.isAmbient(), effect.isVisible());
        event.getEntity().addEffect(updated);
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Player attacker = (event.getSource().getEntity() instanceof Player p) ? p : null;
        int multiplier = getRandomMultiplier(attacker);
        if (multiplier == 0) {
            event.setCanceled(true);
        } else {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        if (multiplier > 1) {
            ItemStack extra = event.getCrafting().copy();
            extra.setCount(extra.getCount() * (multiplier - 1));
            event.getEntity().getInventory().add(extra);
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player) {
            int multiplier = getRandomMultiplier(player);
            if (multiplier > 1 && event.getItem().isEdible()) {
                player.getFoodData().eat(event.getItem().getItem().getFoodProperties().getNutrition() * (multiplier - 1), 0.5F);
            }
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        if (multiplier == 0) {
            event.setCanceled(true);
        } else {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        int multiplier = getRandomMultiplier(player);
        if (multiplier == 0) {
            event.setCanceled(true);
        } else if (multiplier > 1) {
            for (ItemStack stack : event.getDrops()) {
                for (int i = 1; i < multiplier; i++) {
                    ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack.copy());
                    player.level().addFreshEntity(entity);
                }
            }
        }
    }
}
