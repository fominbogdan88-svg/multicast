package com.example.multiaction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MagicWandItem extends Item {

    private static final String[] MODES = {"Молния", "Клыки", "Дыхание Дракона", "Регенерация"};

    public MagicWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        int mode = stack.getOrCreateTag().getInt("WandMode");

        if (player.isShiftKeyDown()) {
            mode = (mode + 1) % MODES.length;
            stack.getTag().putInt("WandMode", mode);
            player.sendSystemMessage(Component.literal("§dРежим палочки изменен на: " + MODES[mode]));
            return InteractionResultHolder.success(stack);
        }

        if (player.experienceLevel < 1 && player.totalExperience < 5) {
            player.sendSystemMessage(Component.literal("§cНедостаточно опыта! (Требуется 5 XP)"));
            return InteractionResultHolder.fail(stack);
        }

        int multiplier = ActionHandler.getMultiplier(player);
        if (multiplier == 0) return InteractionResultHolder.fail(stack);

        player.giveExperiencePoints(-5);

        HitResult hit = player.pick(50.0, 0.0f, false);
        Vec3 hitVec = hit.getLocation();

        switch (mode) {
            case 0: // ⚡ Молнии (Половина попадает строго в точку взгляда)
                for (int i = 0; i < multiplier; i++) {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) {
                        if (i < Math.max(1, multiplier / 2)) {
                            lightning.moveTo(hitVec);
                        } else {
                            lightning.moveTo(hitVec.add((Math.random() - 0.5) * 8, 0, (Math.random() - 0.5) * 8));
                        }
                        level.addFreshEntity(lightning);
                    }
                }
                break;

            case 1: // 🦷 Клыки Призывателя + Слепота V и Замедление V
                for (int i = 0; i < multiplier * 2; i++) {
                    double offsetX = (Math.random() - 0.5) * 8;
                    double offsetZ = (Math.random() - 0.5) * 8;
                    EvokerFangs fangs = EntityType.EVOKER_FANGS.create(level);
                    if (fangs != null) {
                        fangs.moveTo(hitVec.x + offsetX, hitVec.y, hitVec.z + offsetZ);
                        level.addFreshEntity(fangs);
                    }
                }
                AABB boxFangs = new AABB(hitVec.add(-5, -2, -5), hitVec.add(5, 2, 5));
                List<LivingEntity> fangsTargets = level.getEntitiesOfClass(LivingEntity.class, boxFangs, e -> e != player);
                for (LivingEntity t : fangsTargets) {
                    t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 4));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4));
                }
                break;

            case 2: // 🐉 Дыхание Дракона (Не вредит кастеру)
                AABB boxDragon = player.getBoundingBox().inflate(6.0);
                List<LivingEntity> dragonTargets = level.getEntitiesOfClass(LivingEntity.class, boxDragon, e -> e != player);
                for (LivingEntity t : dragonTargets) {
                    t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    t.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
                }
                ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, player.getX(), player.getY(), player.getZ(), 80 * multiplier, 3.0, 1.0, 3.0, 0.1);
                break;

            case 3: // 💖 Регенерация
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200 * multiplier, Math.min(4, multiplier / 4)));
                break;
        }

        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(stack);
    }
}
