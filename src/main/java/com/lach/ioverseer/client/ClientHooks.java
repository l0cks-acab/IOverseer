package com.lach.ioverseer.client;

import com.lach.ioverseer.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openTablet() {
        Minecraft.getInstance().setScreen(new OverseerScreen(Optional.empty()));
    }

    public static void openScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new OverseerScreen(Optional.of(pos)));
    }

    public static void snapshot(Net.Snapshot s) {
        if (Minecraft.getInstance().screen instanceof OverseerScreen os) os.accept(s);
    }

    public static void preview(Net.Preview p) {
        TabletPreview.accept(p);
    }

    public static void alert(Net.Alert a) {
        Minecraft.getInstance().getToastManager().addToast(new AlertToast(a.entry()));
    }
}
