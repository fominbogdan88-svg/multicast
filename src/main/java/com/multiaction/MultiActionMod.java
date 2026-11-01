package com.multiaction;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("multiaction")
public class MultiActionMod {
    public MultiActionMod() {
        MinecraftForge.EVENT_BUS.register(new ActionHandler());
    }
}
