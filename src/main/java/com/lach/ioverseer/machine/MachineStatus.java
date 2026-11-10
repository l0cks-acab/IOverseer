package com.lach.ioverseer.machine;

public enum MachineStatus {
    NO_POWER(0xFFE5484D, "no_power"),
    OUTPUT_FULL(0xFFF59E2E, "output_full"),
    IDLE(0xFFF2D24B, "idle"),
    WORKING(0xFF46C46A, "working"),
    CHECKING(0xFF8C8C8C, "checking"),
    UNLOADED(0xFF5A5A5A, "unloaded");

    public final int color;
    public final String key;

    MachineStatus(int color, String key) {
        this.color = color;
        this.key = key;
    }

    public boolean isProblem() {
        return this == NO_POWER || this == OUTPUT_FULL;
    }

    public static MachineStatus byId(int i) {
        MachineStatus[] v = values();
        return i >= 0 && i < v.length ? v[i] : CHECKING;
    }
}
