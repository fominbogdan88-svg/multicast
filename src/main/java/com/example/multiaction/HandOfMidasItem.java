package com.example.multiaction;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HandOfMidasItem extends Item {

    public HandOfMidasItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        if (target instanceof Warden || target instanceof PiglinBrute || target instanceof EnderDragon || target instanceof WitherBoss) {
            player.sendSystemMessage(Component.literal("§c[Рука Мидаса] Это существо слишком могущественное! Магия отторгается."));
            return InteractionResult.FAIL;
        }

        float maxHealth = target.getMaxHealth();
        int requiredLevel = 0;
        String mobTierName = "Слабый/Мирный";

        if (maxHealth > 30) { requiredLevel = 16; mobTierName = "Элитный/Тяжелый"; } 
        else if (maxHealth > 15) { requiredLevel = 8; mobTierName = "Средний монстр"; }

        if (player.experienceLevel < requiredLevel) {
            player.sendSystemMessage(Component.literal("§e[Рука Мидаса] §cНедостаточно опыта! \n§7Моб: §f" + mobTierName + " (" + (int)maxHealth + " ХП)\n§7Требуется уровень: §l" + requiredLevel + "§7 (Ваш: " + player.experienceLevel + ")"));
            return InteractionResult.FAIL;
        }

        int multiplier = ActionHandler.getRandomMultiplier(player);

        if (multiplier == 0) {
            player.sendSystemMessage(Component.literal("§4[Рука Мидаса] ✘ Срыв! Обмен провален, моб остался цел."));
            player.level().playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        int baseExp = (int) (maxHealth * 5.0f);
        if (baseExp < 10) baseExp = 10;
        int totalExp = baseExp * multiplier;

        player.sendSystemMessage(Component.literal("§6✨ [Рука Мидаса] Успешный обмен! \n§eМножитель: §l" + multiplier + "x \n§aПолучено опыта: §l" + totalExp + " XP"));

        ServerLevel serverLevel = (ServerLevel) player.level();
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, false, false));
        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(), 35, 0.5, 0.5, 0.5, 0.3);
        serverLevel.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.5f);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 2.0f);

        serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, target.getX(), target.getY() + 0.5, target.getZ(), totalExp));
        target.discard();

        return InteractionResult.SUCCESS;
    }
}
