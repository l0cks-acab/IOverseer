package com.lach.ioverseer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SCAN_RADIUS;
    public static final ModConfigSpec.IntValue MAX_MACHINES;
    public static final ModConfigSpec.IntValue IDLE_SECONDS;
    public static final ModConfigSpec.IntValue MAX_ITEM_ONLY_SLOTS;
    public static final ModConfigSpec.IntValue MAX_PINS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SCAN_RADIUS = b.comment("Scan radius in blocks for the tablet and the screen").defineInRange("scanRadius", 32, 4, 128);
        MAX_MACHINES = b.comment("Max machines listed per scan").defineInRange("maxMachines", 256, 16, 2048);
        IDLE_SECONDS = b.comment("Seconds without any change before a machine counts as idle").defineInRange("idleSeconds", 5, 2, 60);
        MAX_ITEM_ONLY_SLOTS = b.comment("Blocks with only an item inventory (no energy/fluid) count as machines when they have at most this many slots. Stops chests showing up.")
                .defineInRange("maxItemOnlySlots", 12, 0, 64);
        MAX_PINS = b.comment("Max pinned machines per tablet").defineInRange("maxPins", 32, 1, 256);
        SPEC = b.build();
    }

    private Config() {}
}
