package com.example.multiaction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MultiActionMod.MODID)
public class ActionHandler {

    private static final Map<UUID, Integer> FORCED_MULTIPLIERS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final int[] MULTIPLIER_WEIGHTS = {30, 470, 180, 100, 60, 40, 30, 20, 15, 15, 10, 8, 6, 3, 2, 1, 10};

    public static int getRandomMultiplier(Player player) {
        if (player != null && FORCED_MULTIPLIERS.containsKey(player.getUUID())) {
            return FORCED_MULTIPLIERS.get(player.getUUID());
        }

        int luckBonus = 0;
        if (player != null && player.hasEffect(MobEffects.LUCK)) {
            MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
            if (luck != null) luckBonus = (luck.getAmplifier() + 1) * 150;
        }

        int totalWeight = 0;
        for (int w : MULTIPLIER_WEIGHTS) totalWeight += w;

        int roll = RANDOM.nextInt(totalWeight);
        if (luckBonus > 0) roll = Math.min(totalWeight - 1, roll + luckBonus);

        int current = 0;
        for (int i = 0; i < MULTIPLIER_WEIGHTS.length; i++) {
            current += MULTIPLIER_WEIGHTS[i];
            if (roll < current) return i;
        }
        return 1;
    }

    private static void notifyPlayer(Player player, int multiplier, String action) {
        if (player != null && multiplier >= 5) {
            player.displayClientMessage(Component.literal("§6🔥 Мультикаст (" + action + "): §l" + multiplier + "x!"), true);
        }
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
                            context.getSource().sendSuccess(() -> Component.literal("§aСброшено на рандом."), false);
                        } else {
                            FORCED_MULTIPLIERS.put(player.getUUID(), level);
                            context.getSource().sendSuccess(() -> Component.literal("§aУстановлен множитель: " + level + "x"), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("luckpotion")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ItemStack potionStack = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LUCK);
                        player.getInventory().add(potionStack);
                        return 1;
                    }))
        );
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide) return;

        if (entity instanceof Projectile projectile) {
            if (projectile.getPersistentData().getBoolean("MulticastDone")) return;
            projectile.getPersistentData().putBoolean("MulticastDone", true);

            if (projectile.getOwner() instanceof Player player) {
                int multiplier = getRandomMultiplier(player);
                if (multiplier == 0) {
                    event.setCanceled(true);
                    return;
                }
                notifyPlayer(player, multiplier, "Снаряды");

                Vec3 motion = projectile.getDeltaMovement();
                for (int i = 1; i < multiplier; i++) {
                    Entity clone = projectile.getType().create(level);
                    if (clone instanceof Projectile pClone) {
                        pClone.setOwner(player);
                        pClone.setPos(projectile.getX(), projectile.getY(), projectile.getZ());
                        pClone.getPersistentData().putBoolean("MulticastDone", true);
                        
                        double spreadX = (RANDOM.nextDouble() - 0.5) * 0.2;
                        double spreadY = (RANDOM.nextDouble() - 0.5) * 0.2;
                        double spreadZ = (RANDOM.nextDouble() - 0.5) * 0.2;
                        pClone.setDeltaMovement(motion.add(spreadX, spreadY, spreadZ));
                        
                        level.addFreshEntity(pClone);
                    }
                }
            }
        }

        if (entity instanceof PrimedTnt tnt) {
            if (tnt.getPersistentData().getBoolean("MulticastDone")) return;
            tnt.getPersistentData().putBoolean("MulticastDone", true);

            Player player = level.getNearestPlayer(tnt.getX(), tnt.getY(), tnt.getZ(), 10.0, false);
            int multiplier = getRandomMultiplier(player);
            
            if (multiplier == 0) {
                event.setCanceled(true);
                return;
            }
            notifyPlayer(player, multiplier, "Взрыв");

            for (int i = 1; i < multiplier; i++) {
                PrimedTnt extraTnt = new PrimedTnt(level, tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getOwner());
                extraTnt.getPersistentData().putBoolean("MulticastDone", true);
                extraTnt.setFuse(tnt.getFuse() + RANDOM.nextInt(20) + 10);
                
                double vx = (RANDOM.nextDouble() - 0.5) * 0.5;
                double vz = (RANDOM.nextDouble() - 0.5) * 0.5;
                extraTnt.setDeltaMovement(vx, 0.2, vz);
                
                level.addFreshEntity(extraTnt);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            int multiplier = getRandomMultiplier(player);
            if (multiplier == 0) {
                event.setCanceled(true);
                return;
            }
            if (multiplier == 1) return;

            notifyPlayer(player, multiplier, "Установка");
            Level level = (Level) event.getLevel();
            BlockPos startPos = event.getPos();
            ItemStack itemInHand = player.getMainHandItem();

            for (int i = 1; i < multiplier; i++) {
                if (!player.isCreative() && itemInHand.getCount() <= 1) break;
                
                BlockPos nextPos = startPos.relative(Direction.UP, i);
                if (level.isEmptyBlock(nextPos)) {
                    level.setBlock(nextPos, event.getPlacedBlock(), 3);
                    if (!player.isCreative()) itemInHand.shrink(1);
                }
            }
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
        if (multiplier == 1) return;

        notifyPlayer(player, multiplier, "Добыча");
        Level level = (Level) event.getLevel();
        Block block = event.getState().getBlock();
        
        ItemStack drop = new ItemStack(block.asItem(), multiplier - 1);
        if (!drop.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(level, event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5, drop);
            level.addFreshEntity(itemEntity);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            int multiplier = getRandomMultiplier(player);
            if (multiplier > 1) {
                notifyPlayer(player, multiplier, "Лут");
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem().copy();
                    stack.setCount(stack.getCount() * (multiplier - 1));
                    ItemEntity extraDrop = new ItemEntity(player.level(), drop.getX(), drop.getY(), drop.getZ(), stack);
                    player.level().addFreshEntity(extraDrop);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;

        int multiplier = getRandomMultiplier(player);
        if (multiplier == 0) {
            event.setCanceled(true);
            return;
        }

        if (multiplier > 1) {
            notifyPlayer(player, multiplier, "Рыбалка");
            for (ItemStack drop : event.getDrops()) {
                for (int i = 1; i < multiplier; i++) {
                    ItemEntity extraItem = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), drop.copy());
                    player.level().addFreshEntity(extraItem);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            ItemStack item = event.getItem();

            if (item.getItem() == Items.POTION && PotionUtils.getPotion(item) == Potions.LUCK) {
                player.removeEffect(MobEffects.LUCK);
                int randomLuckLevel = RANDOM.nextInt(5); 
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, randomLuckLevel));
                player.displayClientMessage(Component.literal("§aВыпитая удача дала уровень: " + (randomLuckLevel + 1)), true);
                return;
            }

            if (item.isEdible()) {
                int multiplier = getRandomMultiplier(player);
                if (multiplier > 1) {
                    notifyPlayer(player, multiplier, "Сытость");
                    FoodProperties food = item.getItem().getFoodProperties(item, player);
                    if (food != null) {
                        player.getFoodData().eat(food.getNutrition() * (multiplier - 1), food.getSaturationModifier());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPotionApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            MobEffectInstance effect = event.getEffectInstance();
            if (effect.getEffect() == MobEffects.LUCK) return; 

            int multiplier = getRandomMultiplier(player);
            if (multiplier == 0) {
                event.setResult(Event.Result.DENY);
                return;
            }

            if (multiplier > 1) {
                int newDuration = effect.getDuration() * multiplier;
                int newAmp = Math.min(4, effect.getAmplifier() + (multiplier > 8 ? 2 : (multiplier > 3 ? 1 : 0)));
                MobEffectInstance newEffect = new MobEffectInstance(effect.getEffect(), newDuration, newAmp, effect.isAmbient(), effect.isVisible());
                player.addEffect(newEffect);
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker) {
            int multiplier = getRandomMultiplier(attacker);
            if (multiplier == 0) event.setCanceled(true);
            else event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        if (multiplier > 1) {
            notifyPlayer(event.getEntity(), multiplier, "Крафт");
            ItemStack extra = event.getCrafting().copy();
            extra.setCount(extra.getCount() * (multiplier - 1));
            event.getEntity().getInventory().add(extra);
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        if (multiplier == 0) event.setCanceled(true);
        else event.setAmount(event.getAmount() * multiplier);
    }
}
