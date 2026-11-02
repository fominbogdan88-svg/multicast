package com.example.multiaction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MagicWandItem extends Item {

    private static final String[] MODES = {
            "§e⚡ Удар Молнии",
            "§8⛰ Гнев Земли (Клыки + Слепота + Замедление)",
            "§d🐉 Дыхание Дракона (Безопасное)",
            "§a♥ Исцеление"
    };

    public MagicWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; 
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        int currentMode = stack.getOrCreateTag().getInt("spell_mode");

        if (player.isShiftKeyDown()) {
            currentMode = (currentMode + 1) % MODES.length;
            stack.getTag().putInt("spell_mode", currentMode);
            player.displayClientMessage(Component.literal("§b[Волшебная Палочка] §fРежим: " + MODES[currentMode]), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
            return InteractionResultHolder.success(stack);
        }

        if (!player.isCreative()) {
            if (player.totalExperience < 5 && player.experienceLevel == 0) {
                player.displayClientMessage(Component.literal("§c[Волшебная Палочка] Недостаточно опыта! (Требуется 5 XP)"), true);
                return InteractionResultHolder.fail(stack);
            }
            player.giveExperiencePoints(-5);
        }

        int multiplier = ActionHandler.getRandomMultiplier(player);

        if (multiplier == 0) {
            player.displayClientMessage(Component.literal("§4✘ Магия рассеялась! (Срыв)"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
            return InteractionResultHolder.success(stack);
        }

        player.displayClientMessage(Component.literal("§5✧ Заклинание: " + MODES[currentMode] + " §d(x" + multiplier + ")"), true);

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        switch (currentMode) {
            case 0: 
                Vec3 targetPos = start.add(look.scale(10));
                for (int i = 0; i < multiplier; i++) {
                    BlockPos strikePos = BlockPos.containing(targetPos.x + (level.random.nextDouble() - 0.5) * multiplier * 2, targetPos.y, targetPos.z + (level.random.nextDouble() - 0.5) * multiplier * 2);
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) {
                        lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
                        lightning.setCause(player instanceof ServerPlayer sp ? sp : null);
                        level.addFreshEntity(lightning);
                    }
                }
                break;
            case 1: 
                int length = 3 + multiplier; 
                for (int i = 1; i <= length; i++) {
                    Vec3 fangPos = start.add(look.scale(i * 1.5));
                    EvokerFangs fangs = new EvokerFangs(level, fangPos.x, player.getY(), fangPos.z, player.getYRot(), i * 2, player);
                    level.addFreshEntity(fangs);
                    AABB box = new AABB(fangPos.x - 2, player.getY() - 1, fangPos.z - 2, fangPos.x + 2, player.getY() + 3, fangPos.z + 2);
                    List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 + multiplier * 20, 0));
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + multiplier * 20, 4));
                    }
                }
                break;
            case 2: 
                Vec3 cloudPos = start.add(look.scale(3 + multiplier * 0.2)); 
                AreaEffectCloud cloud = new AreaEffectCloud(level, cloudPos.x, player.getY(), cloudPos.z);
                cloud.setOwner(player);
                cloud.setRadius(2.0F + multiplier * 0.5F);
                cloud.setDuration(60 + multiplier * 20);
                cloud.setParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH);
                cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1));
                level.addFreshEntity(cloud);
                break;
            case 3: 
                int duration = 100 * multiplier;
                int amplifier = Math.min(4, multiplier / 3);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier));
                player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, Math.min(2, multiplier / 5)));
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                break;
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(stack);
    }
}
