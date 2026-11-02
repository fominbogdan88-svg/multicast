package com.example.multiaction;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class HandOfMidasItem extends Item {

    public HandOfMidasItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        int multiplier = ActionHandler.getMultiplier(player);
        if (multiplier == 0) return InteractionResult.FAIL;

        AABB box = target.getBoundingBox().inflate(multiplier);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);

        boolean used = false;

        for (LivingEntity t : targets) {
            if (isBlacklisted(t)) continue;
            int maxHealth = (int) t.getMaxHealth();

            if (player.experienceLevel < 8 && maxHealth > 15) continue;
            if (player.experienceLevel < 16 && maxHealth > 30) continue;

            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 1));

            int totalExp = (int) (maxHealth * 5.0f * multiplier);
            ExperienceOrb.award((ServerLevel) level, t.position(), totalExp);

            ((ServerLevel) level).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, t.getX(), t.getY() + 1.0, t.getZ(), 35, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.0f);

            t.discard();
            used = true;
        }

        if (used) {
            player.getCooldowns().addCooldown(this, 20);
        }

        return InteractionResult.SUCCESS;
    }

    private boolean isBlacklisted(LivingEntity entity) {
        String name = entity.getType().getDescriptionId();
        return name.contains("warden") || name.contains("piglin_brute") || name.contains("ender_dragon") || name.contains("wither");
    }
}
