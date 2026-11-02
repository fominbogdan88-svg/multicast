package com.example.multiaction;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("multiaction")
public class MultiActionMod {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "multiaction");

    public static final RegistryObject<Item> HAND_OF_MIDAS = ITEMS.register("hand_of_midas", HandOfMidasItem::new);
    public static final RegistryObject<Item> MAGIC_WAND = ITEMS.register("magic_wand", MagicWandItem::new);

    public MultiActionMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::setup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ActionHandler.class);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
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
                            player.sendSystemMessage(Component.literal("§aВыдано зелье удачи!"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("equal")) {
                            ActionHandler.setMode("equal");
                            player.sendSystemMessage(Component.literal("§eРежим вероятностей: РАВНЫЙ (все по ~5.8%)"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("balance")) {
                            ActionHandler.setMode("balance");
                            player.sendSystemMessage(Component.literal("§bРежим вероятностей: СБАЛАНСИРОВАННЫЙ"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("normal")) {
                            ActionHandler.setMode("normal");
                            player.sendSystemMessage(Component.literal("§aРежим вероятностей: СТАНДАРТНЫЙ"));
                            return 1;
                        } else if (args[0].equalsIgnoreCase("gas") || args[0].equalsIgnoreCase("fart")) {
                            ActionHandler.performGasAttack(player);
                            return 1;
                        } else {
                            try {
                                int level = Integer.parseInt(args[0]);
                                ActionHandler.setPlayerMultiplier(player.getUUID(), level);
                                if (level == -1) {
                                    player.sendSystemMessage(Component.literal("§6Личный множитель сброшен."));
                                } else {
                                    player.sendSystemMessage(Component.literal("§6Личный множитель установлен на: " + level + "x"));
                                }
                                return 1;
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("§cОшибка команды."));
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
