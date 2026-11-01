package com.example.multiaction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

public class MagicWandItem extends Item {

    private static final String[] MODES = {
        "⚡ Удар Молнии",
        "💥 Мощный Взрыв",
        "🐉 Дыхание Дракона",
        "💖 Регенерация"
    };

    public MagicWandItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Переключение режимов (Shift + ПКМ)
        if (player.isCrouching()) {
            int currentMode = stack.getOrCreateTag().getInt("WandMode");
            int nextMode = (currentMode + 1) % MODES.length;
            stack.getOrCreateTag().putInt("WandMode", nextMode);

            player.displayClientMessage(Component.literal("§e[Палочка] Режим: §b" + MODES[nextMode]), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
            return InteractionResultHolder.success(stack);
        }

        // Проверка опыта (требуется 15 очков XP)
        int xpCost = 15;
        if (!player.getAbilities().instabuild && player.totalExperience < xpCost) {
            player.displayClientMessage(Component.literal("§cНедостаточно опыта! Требуется " + xpCost + " XP."), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 0.8F);
            return InteractionResultHolder.fail(stack);
        }

        // Мультипликатор 0x - 16x
        int multiplier = ActionHandler.getRandomMultiplier(player);

        if (multiplier == 0) {
            player.displayClientMessage(Component.literal("§c💨 Заклинание сорвалось! (0x)"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
            return InteractionResultHolder.success(stack);
        }

        // Снятие XP
        if (!player.getAbilities().instabuild) {
            player.giveExperiencePoints(-xpCost);
        }

        int mode = stack.getOrCreateTag().getInt("WandMode");
        HitResult hit = player.pick(40.0D, 0.0F, false);
        BlockPos targetPos = (hit.getType() != HitResult.Type.MISS) ? BlockPos.containing(hit.getLocation()) : player.blockPosition().relative(player.getDirection(), 5);

        player.displayClientMessage(Component.literal("§a✨ Магия сработала! (" + multiplier + "x)"), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);

        switch (mode) {
            case 0: // Молния
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

            case 1: // Взрыв
                float power = 2.0F + (multiplier * 0.6F);
                level.explode(player, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, power, Level.ExplosionInteraction.TNT);
                break;

            case 2: // Дыхание Дракона
                AreaEffectCloud cloud = new AreaEffectCloud(level, targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
                cloud.setOwner(player);
                cloud.setRadius(2.0F + (multiplier * 0.5F));
                cloud.setDuration(100 * multiplier);
                cloud.setParticle(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH);
                cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 20, Math.max(0, multiplier / 3)));
                level.addFreshEntity(cloud);
                break;

            case 3: // Регенерация
                int amp = Math.min(4, (multiplier - 1) / 3);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100 * multiplier, amp));
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
        tooltip.add(Component.literal("§8• ПКМ: Каст заклинания (15 XP)"));
        tooltip.add(Component.literal("§d• Поддерживает Мультипликатор (0x - 16x)"));
    }
}
