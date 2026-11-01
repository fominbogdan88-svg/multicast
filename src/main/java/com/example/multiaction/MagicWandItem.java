package com.example.multiaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

public class MagicWandItem extends Item {

    private static final String[] MODES = {
        "⚡ Удар Молнии",
        "🦷 Гнев Земли (Челюсти)",
        "🐉 Дыхание Дракона",
        "💖 Регенерация"
    };

    public MagicWandItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    // Делает предмет визуально зачарованным (переливается магическим блеском)
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (player.isCrouching()) {
            int currentMode = stack.getOrCreateTag().getInt("WandMode");
            int nextMode = (currentMode + 1) % MODES.length;
            stack.getOrCreateTag().putInt("WandMode", nextMode);

            player.displayClientMessage(Component.literal("§e[Палочка] Режим: §b" + MODES[nextMode]), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
            return InteractionResultHolder.success(stack);
        }

        int xpCost = 15;
        if (!player.getAbilities().instabuild && player.totalExperience < xpCost) {
            player.displayClientMessage(Component.literal("§cНедостаточно опыта! (Нужно 15 XP)"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 0.8F);
            return InteractionResultHolder.fail(stack);
        }

        int multiplier = ActionHandler.getRandomMultiplier(player);

        if (multiplier == 0) {
            player.displayClientMessage(Component.literal("§c💨 Заклинание сорвалось! (0x)"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
            return InteractionResultHolder.success(stack);
        }

        if (!player.getAbilities().instabuild) {
            player.giveExperiencePoints(-xpCost);
        }

        int mode = stack.getOrCreateTag().getInt("WandMode");
        HitResult hit = player.pick(40.0D, 0.0F, false);
        BlockPos targetPos = (hit.getType() != HitResult.Type.MISS) ? BlockPos.containing(hit.getLocation()) : player.blockPosition().relative(player.getDirection(), 5);

        player.displayClientMessage(Component.literal("§a✨ Магия сработала! (" + multiplier + "x)"), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);

        switch (mode) {
            case 0:
                for (int i = 0; i < multiplier; i++) {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) {
                        double offsetX = (level.random.nextDouble() - 0.5) * (i > 0 ? 3.0 : 0.0);
                        double offsetZ = (level.random.nextDouble() - 0.5) * (i > 0 ? 3.0 : 0.0);
                        lightning.moveTo(targetPos.getX() + 0.5 + offsetX, targetPos.getY(), targetPos.getZ() + 0.5 + offsetZ);
                        level.addFreshEntity(lightning);
                    }
                }
                break;

            case 1:
                int radius = Math.min(10, multiplier);
                for (int i = 0; i < multiplier * 2; i++) {
                    double angle = i * (Math.PI * 2 / (multiplier * 2));
                    double dx = targetPos.getX() + 0.5 + Math.cos(angle) * radius * level.random.nextDouble();
                    double dz = targetPos.getZ() + 0.5 + Math.sin(angle) * radius * level.random.nextDouble();
                    EvokerFangs fangs = new EvokerFangs(level, dx, targetPos.getY(), dz, 0, 0, player);
                    level.addFreshEntity(fangs);
                }
                break;

            case 2:
                double effectRadius = 2.0 + (multiplier * 0.5);
                AABB box = new AABB(targetPos).inflate(effectRadius);
                List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5, multiplier * 20, effectRadius / 2, 0.5, effectRadius / 2, 0.05);
                }

                for (LivingEntity entity : entities) {
                    if (entity != player) {
                        entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 100 * multiplier, Math.min(3, multiplier / 4)));
                    }
                }
                break;

            case 3:
                int regenAmplifier = Math.min(4, (multiplier - 1) / 3);
                int durationTicks = 160 + (multiplier * 60);

                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, durationTicks, regenAmplifier, false, true, true));

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 5 + multiplier * 2, 0.4, 0.6, 0.4, 0.1);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);
                break;
        }

        player.getCooldowns().addCooldown(this, 15);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int mode = stack.getOrCreateTag().getInt("WandMode");
        tooltip.add(Component.literal("§7Текущий режим: §b" + MODES[mode]));
        tooltip.add(Component.literal("§8• Shift + ПКМ: Смена режима"));
        tooltip.add(Component.literal("§8• ПКМ: Каст (15 XP)"));
    }
}
