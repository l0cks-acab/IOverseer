package com.lach.overseer;

import com.lach.overseer.net.Net;
import com.lach.overseer.server.ServerEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Overseer.MODID)
public class Overseer {
    public static final String MODID = "overseer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Overseer(IEventBus modBus, ModContainer container) {
        Registry.register(modBus);
        modBus.addListener(Net::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
