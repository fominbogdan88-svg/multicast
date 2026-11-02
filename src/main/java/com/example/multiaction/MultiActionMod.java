package com.example.multiaction;
package com.example.multiaction;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("multiaction")
public class MultiActionMod {

    public MultiActionMod() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ActionHandler.class);
        MinecraftForge.EVENT_BUS.register(MagicWandItem.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("multiaction")
            .then(Commands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof Player player)) return 0;
                    String[] args = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "args").split(" ");

                    if (args.length == 1) {
                        if (args[0].equalsIgnoreCase("luckpotion")) {
                            ItemStack potion = new ItemStack(Items.POTION);
                            PotionUtils.setPotion(potion, Potions.LUCK);
                            player.getInventory().add(potion);
                            player.sendSystemMessage(Component.literal("Выдано зелье удачи!"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("equal")) {
                            ActionHandler.setMode("equal");
                            player.sendSystemMessage(Component.literal("Режим вероятностей: РАВНЫЙ (все по ~5.8%)"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("balance")) {
                            ActionHandler.setMode("balance");
                            player.sendSystemMessage(Component.literal("Режим вероятностей: СБАЛАНСИРОВАННЫЙ (меньше шансов на х16)"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("normal")) {
                            ActionHandler.setMode("normal");
                            player.sendSystemMessage(Component.literal("Режим вероятностей: СТАНДАРТНЫЙ"));
                            return 1;
                        } else {
                            try {
                                int level = Integer.parseInt(args[0]);
                                ActionHandler.setPlayerMultiplier(player.getUUID(), level);
                                if (level == -1) {
                                    player.sendSystemMessage(Component.literal("Мой личный множитель сброшен."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Мой личный множитель установлен на: " + level + "x"));
                                }
                                return 1;
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Ошибка: введите число, equal, balance, normal или luckpotion."));
                                return 0;
                            }
                        }
                    }
                    return 0;
                })
            )
        );
    }
}
