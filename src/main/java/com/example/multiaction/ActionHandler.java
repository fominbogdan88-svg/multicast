package com.example.multiaction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionHandler {

    private static final Map<UUID, Integer> playerForcedMultipliers = new ConcurrentHashMap<>();
    private static String mode = "normal";

    public static class CasinoAnim {
        int ticks;
        int target;
        public CasinoAnim(int ticks, int target) { this.ticks = ticks; this.target = target; }
    }
    private static final Map<UUID, CasinoAnim> casinoAnims = new ConcurrentHashMap<>();

    public static void setPlayerMultiplier(UUID uuid, int mult) {
        if (mult == -1) playerForcedMultipliers.remove(uuid);
        else playerForcedMultipliers.put(uuid, mult);
    }

    public static void setMode(String newMode) { mode = newMode; }

    public static int getMultiplier(Player player) {
        int mult = calculateMultiplier(player);
        if (mult > 0) {
            casinoAnims.put(player.getUUID(), new CasinoAnim(10, mult));
        }
        return mult;
    }

    private static int calculateMultiplier(Player player) {
        if (playerForcedMultipliers.containsKey(player.getUUID())) {
            return playerForcedMultipliers.get(player.getUUID());
        }

        double roll = Math.random() * 100.0;
        int luckLevel = 0;
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.LUCK)) {
            luckLevel = player.getEffect(net.minecraft.world.effect.MobEffects.LUCK).getAmplifier() + 1;
            roll += luckLevel * 1.5; 
        }

        if (mode.equals("equal")) {
            int[] vals = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
            return vals[(int)(Math.random() * vals.length)];
        } else if (mode.equals("balance")) {
            if (roll < 10) return 0;
            if (roll < 70) return 1;
            if (roll < 85) return 2;
            if (roll < 93) return 3;
            if (roll < 97) return 4;
            if (roll < 99) return 5;
            return 16;
        }

        if (roll < 3.0) return 0;
        if (roll < 50.0) return 1;
        if (roll < 70.0) return 2;
        if (roll < 80.0) return 3;
        if (roll < 88.0) return 4;
        if (roll < 93.0) return 5;
        if (roll < 95.0) return 6;
        if (roll < 96.0) return 7;
        if (roll < 97.0) return 8;
        if (roll < 98.0) return 9;
        if (roll < 99.0) return 10;
        return 16;
    }

    private static int getVolumeSize(int mult) {
        if (mult >= 16) return 4;
        if (mult >= 9) return 3;
        if (mult >= 4) return 2;
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            CasinoAnim anim = casinoAnims.get(event.player.getUUID());
            if (anim != null) {
                anim.ticks--;
                if (anim.ticks <= 0) {
                    event.player.displayClientMessage(Component.literal("§e§l🎰 МНОЖИТЕЛЬ: " + anim.target + "x! 🎰"), true);
                    event.player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    casinoAnims.remove(event.player.getUUID());
                } else if (anim.ticks % 2 == 0) {
                    int randomDisp = (int)(Math.random() * 17);
                    event.player.displayClientMessage(Component.literal("§7🎰 Крутим... §a" + randomDisp + "x §7🎰"), true);
                    event.player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 2.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        int mult = getMultiplier(player);
        if (mult <= 1) return;

        Level level = (Level) player.level();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        int size = getVolumeSize(mult);

        // Умножаем дроп с первого блока
        List<ItemStack> baseDrops = Block.getDrops(state, (ServerLevel) level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        for (ItemStack drop : baseDrops) {
            ItemStack extra = drop.copy();
            extra.setCount(extra.getCount() * (mult - 1));
            Block.popResource(level, pos, extra);
        }

        // Ломаем объем и умножаем дроп каждого блока на мультипликатор
        for (int dx = -size/2; dx <= size/2; dx++) {
            for (int dy = -size/2; dy <= size/2; dy++) {
                for (int dz = -size/2; dz <= size/2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).getBlock() == state.getBlock()) {
                        List<ItemStack> vDrops = Block.getDrops(state, (ServerLevel) level, p, level.getBlockEntity(p), player, player.getMainHandItem());
                        for (ItemStack drop : vDrops) {
                            ItemStack mDrop = drop.copy();
                            mDrop.setCount(mDrop.getCount() * mult);
                            Block.popResource(level, p, mDrop);
                        }
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        int mult = getMultiplier(player);
        if (mult <= 1) return;

        Level level = (Level) player.level();
        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();
        int size = getVolumeSize(mult);

        for(int dx = 0; dx < size; dx++) {
            for(int dy = 0; dy < size; dy++) {
                for(int dz = 0; dz < size; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).canBeReplaced()) {
                        level.setBlock(p, state, 3);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() == null || event.getContext().getLevel().isClientSide) return;
        if (event.getToolAction() == ToolActions.AXE_STRIP) {
            BlockState stripped = event.getFinalState();
            int mult = getMultiplier(event.getPlayer());
            if (mult > 1 && stripped != null) {
                int size = getVolumeSize(mult);
                Level level = (Level) event.getContext().getLevel();
                BlockPos pos = event.getContext().getClickedPos();
                Block original = event.getState().getBlock();
                for (int dx = -size/2; dx <= size/2; dx++) {
                    for (int dy = -size/2; dy <= size/2; dy++) {
                        for (int dz = -size/2; dz <= size/2; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos p = pos.offset(dx, dy, dz);
                            if (level.getBlockState(p).getBlock() == original) {
                                level.setBlock(p, stripped, 3);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBucketFill(FillBucketEvent event) {
        if (event.getLevel().isClientSide || event.getEntity() == null) return;
        Player player = event.getEntity();
        int mult = getMultiplier(player);
        if (mult <= 1) return;

        if (event.getTarget() instanceof net.minecraft.world.phys.BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            Level level = event.getLevel();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LiquidBlock liquid) {
                int size = getVolumeSize(mult);
                int count = 1;
                for (int dx = -size/2; dx <= size/2; dx++) {
                    for (int dy = -size/2; dy <= size/2; dy++) {
                        for (int dz = -size/2; dz <= size/2; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos p = pos.offset(dx, dy, dz);
                            if (level.getBlockState(p).getBlock() == state.getBlock() && level.getBlockState(p).getValue(LiquidBlock.LEVEL) == 0) {
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                                count++;
                            }
                        }
                    }
                }
                Item bucketItem = liquid.getFluid().getBucket();
                ItemStack filled = new ItemStack(bucketItem, Math.min(count, 64));
                event.setFilledBucket(filled);
                event.setResult(Event.Result.ALLOW);
            }
        }
    }

    @SubscribeEvent
    public static void onElytraFirework(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (player.isFallFlying() && event.getItemStack().getItem() == Items.FIREWORK_ROCKET) {
            int mult = getMultiplier(player);
            if (mult > 1) {
                Vec3 look = player.getLookAngle();
                player.addDeltaMovement(look.x * mult * 0.15, look.y * mult * 0.15, look.z * mult * 0.15);
                player.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (event.getTarget() instanceof SnowGolem golem && event.getItemStack().getItem() == Items.SHEARS) {
            if (golem.readyForShearing()) {
                int mult = getMultiplier(event.getEntity());
                if (mult > 1) {
                    for (int i = 1; i < mult; i++) {
                        ItemEntity pumpkin = new ItemEntity(event.getLevel(), golem.getX(), golem.getY() + 1, golem.getZ(), new ItemStack(Items.CARVED_PUMPKIN));
                        event.getLevel().addFreshEntity(pumpkin);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFireworkSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof FireworkRocketEntity firework) {
            if (firework.getOwner() instanceof Player player) {
                if (firework.getTags().contains("multiplied")) return;
                firework.addTag("multiplied");

                int mult = getMultiplier(player);
                if (mult > 1) {
                    for (int i = 1; i < mult; i++) {
                        FireworkRocketEntity clone = new FireworkRocketEntity(event.getLevel(), firework.getItem(), firework.getOwner());
                        clone.setPos(firework.getX(), firework.getY(), firework.getZ());
                        clone.setDeltaMovement(firework.getDeltaMovement().add(
                            (Math.random() - 0.5) * 0.5,
                            (Math.random() - 0.5) * 0.5,
                            (Math.random() - 0.5) * 0.5
                        ));
                        clone.addTag("multiplied");
                        event.getLevel().addFreshEntity(clone);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player causer = event.getCauser();
        if (causer == null && event.getParentA() instanceof Animal animal) {
            causer = animal.getLoveCause() != null ? animal.getLoveCause() : null;
        }
        if (causer != null && !causer.level().isClientSide && event.getChild() != null) {
            int mult = getMultiplier(causer);
            if (mult > 1) {
                for (int i = 1; i < mult; i++) {
                    LivingEntity clone = (LivingEntity) event.getChild().getType().create(causer.level());
                    if (clone != null) {
                        clone.setPos(event.getChild().getX(), event.getChild().getY(), event.getChild().getZ());
                        if (clone instanceof Animal animalClone) animalClone.setAge(-24000);
                        causer.level().addFreshEntity(clone);
                    }
                }
            }
        }
    }
}
