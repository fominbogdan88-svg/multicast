package com.example.multiaction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = MultiActionMod.MODID)
public class ActionHandler {

    private static final Map<UUID, Integer> FORCED_MULTIPLIERS = new HashMap<>();
    private static final Set<UUID> EQUAL_CHANCE_PLAYERS = new HashSet<>();
    private static final Random RANDOM = new Random();
    private static final int[] MULTIPLIER_WEIGHTS = {30, 470, 180, 100, 60, 40, 30, 20, 15, 15, 10, 8, 6, 3, 2, 1, 10};

    private static final ThreadLocal<Boolean> IS_BREAKING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> POTION_LOCK = ThreadLocal.withInitial(() -> false);

    public static int getRandomMultiplier(Player player) {
        if (player != null && FORCED_MULTIPLIERS.containsKey(player.getUUID())) return FORCED_MULTIPLIERS.get(player.getUUID());
        if (player != null && EQUAL_CHANCE_PLAYERS.contains(player.getUUID())) return RANDOM.nextInt(17);
        
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

    // НОВОЕ: Перевод уровня мультикаста в объем блоков (кубы)
    private static int getVolumeLimit(int multiplier) {
        if (multiplier == 0) return 0;
        if (multiplier == 16) return 64; // 16x = куб 4x4x4 (64 блока)
        if (multiplier >= 9) return 27;  // 9x-15x = куб 3x3x3 (27 блоков)
        if (multiplier >= 4) return 8;   // 4x-8x = куб 2x2x2 (8 блоков)
        return multiplier;               // 1x-3x = 1-3 блока
    }

    private static void notifyPlayer(Player player, int rolls, String actionType) {
        if (player == null || player.level().isClientSide) return;
        if (rolls == 0) {
            player.displayClientMessage(Component.literal("§4✘ Срыв! (" + actionType + ") FAILED!"), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
        } else if (rolls >= 10) {
package com.example.multiaction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = MultiActionMod.MODID)
public class ActionHandler {

    private static final Map<UUID, Integer> FORCED_MULTIPLIERS = new HashMap<>();
    private static final Set<UUID> EQUAL_CHANCE_PLAYERS = new HashSet<>();
    private static final Random RANDOM = new Random();
    private static final int[] MULTIPLIER_WEIGHTS = {30, 470, 180, 100, 60, 40, 30, 20, 15, 15, 10, 8, 6, 3, 2, 1, 10};

    private static final ThreadLocal<Boolean> IS_BREAKING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> POTION_LOCK = ThreadLocal.withInitial(() -> false);

    public static int getRandomMultiplier(Player player) {
        if (player != null && FORCED_MULTIPLIERS.containsKey(player.getUUID())) return FORCED_MULTIPLIERS.get(player.getUUID());
        if (player != null && EQUAL_CHANCE_PLAYERS.contains(player.getUUID())) return RANDOM.nextInt(17);
        
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

    private static int getVolumeLimit(int multiplier) {
        if (multiplier == 0) return 0;
        if (multiplier == 16) return 64; 
        if (multiplier >= 9) return 27;  
        if (multiplier >= 4) return 8;   
        return multiplier;               
    }

    private static void notifyPlayer(Player player, int rolls, String actionType) {
        if (player == null || player.level().isClientSide) return;
        if (rolls == 0) {
            player.displayClientMessage(Component.literal("§4✘ Срыв! (" + actionType + ") FAILED!"), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
        } else if (rolls >= 10) {
            player.displayClientMessage(Component.literal("§6★ MEGA MULTI-" + actionType + "! x" + rolls), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
        } else if (rolls >= 5) {
            player.displayClientMessage(Component.literal("§d✦ SUPER Multi-" + actionType + "! x" + rolls), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
        } else if (rolls > 1) {
            player.displayClientMessage(Component.literal("§e⚡ Multi-" + actionType + "! x" + rolls), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }

    public static void triggerFart(ServerPlayer player) {
        int multiplier = getRandomMultiplier(player);
        Level level = player.level();

        if (multiplier == 0) {
            player.displayClientMessage(Component.literal("§4✘ Вы попытались пукнуть, но обделались..."), true);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
            return;
        }

        notifyPlayer(player, multiplier, "Fart");

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.5f, 0.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.5f, 0.5f);

        AreaEffectCloud cloud = new AreaEffectCloud(level, player.getX(), player.getY() + 0.2, player.getZ());
        cloud.setOwner(player);
        cloud.setRadius(1.5F + (multiplier * 0.5F));
        cloud.setDuration(40 + (multiplier * 15));
        cloud.setParticle(net.minecraft.core.particles.ParticleTypes.SNEEZE);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        cloud.setFixedColor(0x33FF33);
        level.addFreshEntity(cloud);

        String name = player.getName().getString();
        Component chatMsg;
        if (multiplier >= 10) {
            chatMsg = Component.literal("§2💨 ВНИМАНИЕ! Игрок §l" + name + "§2 устроил ГАЗОВУЮ АТАКУ! (" + multiplier + "x)");
        } else if (multiplier >= 5) {
            chatMsg = Component.literal("§a💨 Игрок §l" + name + "§a мощно пёрнул! (" + multiplier + "x)");
        } else {
            chatMsg = Component.literal("§7💨 Игрок " + name + " пёрнул.");
        }
        
        if (player.getServer() != null) player.getServer().getPlayerList().broadcastSystemMessage(chatMsg, false);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("multiaction").requires(source -> source.hasPermission(2))
            .then(Commands.argument("level", IntegerArgumentType.integer(-1, 16)).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                int level = IntegerArgumentType.getInteger(context, "level");
                if (level == -1) {
                    FORCED_MULTIPLIERS.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal("§a[MultiAction] Множитель сброшен на рандом!"));
                } else {
                    FORCED_MULTIPLIERS.put(player.getUUID(), level);
                    EQUAL_CHANCE_PLAYERS.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal("§a[MultiAction] Принудительный множитель установлен: §l" + level + "x"));
                }
                return 1;
            }))
            .then(Commands.literal("luckpotion").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                player.getInventory().add(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LUCK));
                player.sendSystemMessage(Component.literal("§a[MultiAction] Выдано Зелье Удачи!"));
                return 1;
            }))
            .then(Commands.literal("equal").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                UUID id = player.getUUID();
                if (EQUAL_CHANCE_PLAYERS.contains(id)) {
                    EQUAL_CHANCE_PLAYERS.remove(id);
                    player.sendSystemMessage(Component.literal("§e[MultiAction] Режим равных шансов §cВЫКЛЮЧЕН§e.\n§7Вернуты обычные веса."));
                } else {
                    EQUAL_CHANCE_PLAYERS.add(id);
                    FORCED_MULTIPLIERS.remove(id);
                    player.sendSystemMessage(Component.literal("§e[MultiAction] Режим равных шансов §aВКЛЮЧЁН§e!\n§7Теперь 0x..16x выпадают с одинаковой вероятностью (~5.8%)."));
                }
                return 1;
            }))
        );
    }

    // ИСПРАВЛЕННЫЙ МЕТОД ОПРЕДЕЛЕНИЯ ИГРОКА ПРИ РАЗМНОЖЕНИИ
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player player = null;
        if (event.getParentA() instanceof Animal animal) {
            player = animal.getLoveCause();
        }
        if (player == null && event.getParentB() instanceof Animal animal) {
            player = animal.getLoveCause();
        }

        if (player != null && !player.level().isClientSide) {
            int multiplier = getRandomMultiplier(player);
            notifyPlayer(player, multiplier, "Breeding");
            if (multiplier == 0) {
                event.setCanceled(true);
            } else if (multiplier > 1) {
                Level level = player.level();
                for (int i = 1; i < multiplier; i++) {
                    Entity extraBaby = event.getChild().getType().create(level);
                    if (extraBaby instanceof AgeableMob ageable) {
                        ageable.setBaby(true);
                        ageable.setPos(event.getParentA().getX() + (RANDOM.nextDouble() - 0.5), event.getParentA().getY(), event.getParentA().getZ() + (RANDOM.nextDouble() - 0.5));
                        level.addFreshEntity(ageable);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;

        int multiplier = getRandomMultiplier(player);
        notifyPlayer(player, multiplier, "Drop");

        if (multiplier == 0) event.setCanceled(true); 
        else if (multiplier > 1) {
            ItemStack stack = event.getEntity().getItem();
            for (int i = 1; i < multiplier; i++) {
                ItemEntity extra = new ItemEntity(player.level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack.copy());
                extra.setPickUpDelay(40); 
                extra.setDeltaMovement(event.getEntity().getDeltaMovement().add(
                        (RANDOM.nextDouble() - 0.5) * 0.2, (RANDOM.nextDouble() - 0.5) * 0.2, (RANDOM.nextDouble() - 0.5) * 0.2
                ));
                player.level().addFreshEntity(extra);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide) return;

        if (entity instanceof Projectile projectile && !(projectile instanceof FishingHook) && !projectile.getPersistentData().getBoolean("MulticastDone")) {
            projectile.getPersistentData().putBoolean("MulticastDone", true);
            if (projectile.getOwner() instanceof Player player) {
                int multiplier = getRandomMultiplier(player);
                notifyPlayer(player, multiplier, "Shot");
                if (multiplier == 0) { event.setCanceled(true); return; }
                
                Vec3 motion = projectile.getDeltaMovement();
                for (int i = 1; i < multiplier; i++) {
                    Entity clone = projectile.getType().create(level);
                    if (clone instanceof Projectile pClone) {
                        pClone.setOwner(player);
                        pClone.setPos(projectile.getX(), projectile.getY(), projectile.getZ());
                        pClone.getPersistentData().putBoolean("MulticastDone", true);
                        pClone.setDeltaMovement(motion.add((RANDOM.nextDouble() - 0.5) * 0.2, (RANDOM.nextDouble() - 0.5) * 0.2, (RANDOM.nextDouble() - 0.5) * 0.2));
                        level.addFreshEntity(pClone);
                    }
                }
            }
        }

        if (entity instanceof PrimedTnt tnt && !tnt.getPersistentData().getBoolean("MulticastDone")) {
            tnt.getPersistentData().putBoolean("MulticastDone", true);
            Player player = level.getNearestPlayer(tnt.getX(), tnt.getY(), tnt.getZ(), 10.0, false);
            int multiplier = getRandomMultiplier(player);
            notifyPlayer(player, multiplier, "Explosion");
            if (multiplier == 0) { event.setCanceled(true); return; }
            
            for (int i = 1; i < multiplier; i++) {
                PrimedTnt extraTnt = new PrimedTnt(level, tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getOwner());
                extraTnt.getPersistentData().putBoolean("MulticastDone", true);
                extraTnt.setFuse(tnt.getFuse() + RANDOM.nextInt(20) + 10);
                extraTnt.setDeltaMovement((RANDOM.nextDouble() - 0.5) * 0.5, 0.2, (RANDOM.nextDouble() - 0.5) * 0.5);
                level.addFreshEntity(extraTnt);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            int multiplier = getRandomMultiplier(player);
            notifyPlayer(player, multiplier, "Place");
            if (multiplier == 0) { event.setCanceled(true); return; }
            
            int limit = getVolumeLimit(multiplier);
            if (limit <= 1) return; 

            Level level = (Level) event.getLevel();
            BlockPos startPos = event.getPos();
            BlockState placedState = event.getPlacedBlock();
            ItemStack itemInHand = player.getMainHandItem();

            int placed = 0;
            Queue<BlockPos> queue = new LinkedList<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(startPos);
            visited.add(startPos);

            while (!queue.isEmpty() && placed < limit - 1) {
                if (!player.isCreative() && itemInHand.isEmpty()) break;
                BlockPos current = queue.poll();
                
                for (BlockPos neighbor : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                    BlockPos copyPos = neighbor.immutable();
                    if (!visited.contains(copyPos)) {
                        visited.add(copyPos);
                        if (level.isEmptyBlock(copyPos)) {
                            level.setBlock(copyPos, placedState, 3);
                            if (!player.isCreative()) itemInHand.shrink(1);
                            placed++;
                            queue.add(copyPos);
                            if (placed >= limit - 1 || (!player.isCreative() && itemInHand.isEmpty())) break;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getLevel().isClientSide() || IS_BREAKING.get()) return;

        int multiplier = getRandomMultiplier(player);
        notifyPlayer(player, multiplier, "Break");
        if (multiplier == 0) { event.setCanceled(true); return; }
        
        int limit = getVolumeLimit(multiplier);
        if (limit <= 1) return;

        Level level = (Level) event.getLevel();
        BlockState targetState = event.getState();

        IS_BREAKING.set(true);
        try {
            int broken = 0;
            Queue<BlockPos> queue = new LinkedList<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(event.getPos());
            visited.add(event.getPos());

            while (!queue.isEmpty() && broken < limit - 1) {
                BlockPos current = queue.poll();
                for (BlockPos neighbor : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                    BlockPos copyPos = neighbor.immutable();
                    if (!visited.contains(copyPos) && level.getBlockState(copyPos).is(targetState.getBlock())) {
                        visited.add(copyPos);
                        level.destroyBlock(copyPos, true, player);
                        broken++;
                        queue.add(copyPos);
                        if (broken >= limit - 1) break;
                    }
                }
            }
        } finally { IS_BREAKING.set(false); }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            int multiplier = getRandomMultiplier(player);
            notifyPlayer(player, multiplier, "Loot");
            if (multiplier > 1) {
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem().copy();
                    stack.setCount(stack.getCount() * (multiplier - 1));
                    player.level().addFreshEntity(new ItemEntity(player.level(), drop.getX(), drop.getY(), drop.getZ(), stack));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        int multiplier = getRandomMultiplier(player);
        notifyPlayer(player, multiplier, "Fishing");
        if (multiplier == 0) { event.getDrops().clear(); return; }
        if (multiplier > 1) {
            for (ItemStack drop : event.getDrops()) {
                for (int i = 1; i < multiplier; i++) player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), drop.copy()));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (event.getTarget() instanceof IForgeShearable shearable && event.getItemStack().is(Items.SHEARS)) {
            if (shearable.isShearable(event.getItemStack(), event.getLevel(), event.getTarget().blockPosition())) {
                int multiplier = getRandomMultiplier(event.getEntity());
                notifyPlayer(event.getEntity(), multiplier, "Shear");
                if (multiplier == 0) { event.setCanceled(true); return; }
                if (multiplier > 1) {
                    List<ItemStant.setCanceled(true); return; }
                if (multiplier > 1) {
                    List<ItemStack> drops = shearable.onSheared(event.getEntity(), event.getItemStack(), event.getLevel(), event.getTarget().blockPosition(), 0);
                    for (ItemStack drop : drops) {
                        for (int i = 1; i < multiplier; i++) event.getLevel().addFreshEntity(new ItemEntity(event.getLevel(), event.getTarget().getX(), event.getTarget().getY() + 1, event.getTarget().getZ(), drop.copy()));
                    }
                    event.getItemStack().hurtAndBreak(1, event.getEntity(), (p) -> p.broadcastBreakEvent(event.getHand()));
                    event.getLevel().playSound(null, event.getTarget(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    event.setCanceled(true);
                    event.getEntity().swing(event.getHand());
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
                int luckLvl = RANDOM.nextInt(5); 
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, luckLvl));
                player.displayClientMessage(Component.literal("§aУдача уровня: " + (luckLvl + 1)), true);
                return;
            }
            if (item.isEdible()) {
                int multiplier = getRandomMultiplier(player);
                notifyPlayer(player, multiplier, "Eat");
                if (multiplier == 0) {
                    FoodProperties food = item.getItem().getFoodProperties(item, player);
                    if (food != null) player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - food.getNutrition()));
                } else if (multiplier > 1) {
                    FoodProperties food = item.getItem().getFoodProperties(item, player);
                    if (food != null) player.getFoodData().eat(food.getNutrition() * (multiplier - 1), food.getSaturationModifier());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPotionApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            if (POTION_LOCK.get()) return; 
            MobEffectInstance effect = event.getEffectInstance();
            if (effect.getEffect() == MobEffects.LUCK) return; 
            int multiplier = getRandomMultiplier(player);
            if (multiplier == 0) { event.setResult(Event.Result.DENY); return; }
            if (multiplier > 1) {
                POTION_LOCK.set(true);
                try {
                    int newDur = effect.getDuration() * multiplier;
                    if (newDur < 0) newDur = Integer.MAX_VALUE; 
                    player.addEffect(new MobEffectInstance(effect.getEffect(), newDur, Math.min(4, effect.getAmplifier() + (multiplier > 8 ? 2 : (multiplier > 3 ? 1 : 0))), effect.isAmbient(), effect.isVisible()));
                    event.setResult(Event.Result.DENY);
                } finally { POTION_LOCK.set(false); }
            }
        }
    }

    @SubscribeEvent
    public static void onSmelting(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        int multiplier = getRandomMultiplier(player);
        notifyPlayer(player, multiplier, "Smelt");
        if (multiplier == 0) event.getSmelting().setCount(0);
        else if (multiplier > 1) {
            for (int i = 1; i < multiplier; i++) player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), event.getSmelting().copy()));
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        notifyPlayer(event.getEntity(), multiplier, "Craft");
        if (multiplier == 0) event.getCrafting().setCount(0);
        else if (multiplier > 1) {
            ItemStack extra = event.getCrafting().copy();
            extra.setCount(extra.getCount() * (multiplier - 1));
            event.getEntity().getInventory().add(extra);
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        int multiplier = getRandomMultiplier(player);
        notifyPlayer(player, multiplier, "Smithing");
        if (multiplier > 1) {
            for (int i = 1; i < multiplier; i++) player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), event.getOutput().copy()));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker) {
            int multiplier = getRandomMultiplier(attacker);
            notifyPlayer(attacker, multiplier, "Damage");
            if (multiplier == 0) event.setCanceled(true);
            else event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int multiplier = getRandomMultiplier(event.getEntity());
        notifyPlayer(event.getEntity(), multiplier, "XP");
        if (multiplier == 0) event.setCanceled(true);
        else event.setAmount(event.getAmount() * multiplier);
    }
}
