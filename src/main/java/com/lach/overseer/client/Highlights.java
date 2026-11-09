package com.lach.overseer.client;

import com.lach.overseer.machine.MachineEntry;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Machines the player ctrl-clicked to find in the world. Client only, cleared on reach. */
public final class Highlights {
    public record Target(GlobalPos pos, ResourceLocation block) {}

    static final Map<GlobalPos, Target> TARGETS = new LinkedHashMap<>();

    private Highlights() {}

    static GlobalPos key(MachineEntry e) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, e.dim()), e.pos());
    }

    public static boolean has(GlobalPos p) {
        return TARGETS.containsKey(p);
    }

    /** @return true if now highlighted */
    public static boolean toggle(MachineEntry e) {
        GlobalPos k = key(e);
        if (TARGETS.remove(k) != null) return false;
        TARGETS.put(k, new Target(k, e.block()));
        return true;
    }

    public static void clear() {
        TARGETS.clear();
    }
}
