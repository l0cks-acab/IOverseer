package com.lach.overseer.machine;

import com.lach.overseer.Config;
import com.lach.overseer.Overseer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

/** Finds machines and works out their status from NeoForge capabilities. Server side only. */
public final class Machines {
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, Overseer.id("ignored"));
    public static final TagKey<Block> FORCED = TagKey.create(Registries.BLOCK, Overseer.id("machines"));
    private static final Set<String> ACTIVE_PROPS = Set.of("lit", "active", "working", "running", "is_active", "on");

    private record History(long sig, long lastChange, long firstSeen, long lastSeen) {}

    private static final Map<GlobalPos, History> HISTORY = new HashMap<>();
    private static long lastPrune;

    private Machines() {}

    // ---------- discovery ----------

    public static List<MachineEntry> scan(ServerLevel level, BlockPos center, int radius, Collection<GlobalPos> pinned) {
        int max = Config.MAX_MACHINES.get();
        List<BlockEntity> found = new ArrayList<>();
        int r2 = radius * radius;
        int cx0 = (center.getX() - radius) >> 4, cx1 = (center.getX() + radius) >> 4;
        int cz0 = (center.getZ() - radius) >> 4, cz1 = (center.getZ() + radius) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || be.getBlockPos().distSqr(center) > r2) continue;
                    if (isMachine(level, be)) found.add(be);
                }
            }
        }
        found.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));
        Set<GlobalPos> pinSet = new HashSet<>(pinned);
        List<MachineEntry> out = new ArrayList<>();
        Set<GlobalPos> seen = new HashSet<>();
        for (BlockEntity be : found) {
            if (out.size() >= max) break;
            GlobalPos gp = GlobalPos.of(level.dimension(), be.getBlockPos());
            seen.add(gp);
            out.add(sample(level, be).withPinned(pinSet.contains(gp)));
        }
        // pinned machines outside the scan area, possibly other dimensions
        for (GlobalPos gp : pinned) {
            if (seen.contains(gp)) continue;
            MachineEntry e = samplePinned(level.getServer().getLevel(gp.dimension()), gp);
            if (e != null) out.add(e);
        }
        return out;
    }

    @Nullable
    public static MachineEntry samplePinned(@Nullable ServerLevel level, GlobalPos gp) {
        ResourceKey<Level> dim = gp.dimension();
        if (level == null) return null;
        BlockPos pos = gp.pos();
        if (!level.isLoaded(pos)) {
            return new MachineEntry(dim.identifier(), pos, BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR),
                    MachineStatus.UNLOADED, 0, 0, 0, 0, 0, 0, true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return sample(level, be).withPinned(true);
    }

    public static boolean isMachine(Level level, BlockEntity be) {
        BlockState state = be.getBlockState();
        if (state.is(IGNORED)) return false;
        if (state.is(FORCED)) return true;
        if (be instanceof AbstractFurnaceBlockEntity) return true;
        if (Create.isKinetic(be)) {
            return cap(level, be, Capabilities.Item.BLOCK) != null || cap(level, be, Capabilities.Fluid.BLOCK) != null;
        }
        EnergyHandler energy = cap(level, be, Capabilities.Energy.BLOCK);
        ResourceHandler<ItemResource> items = cap(level, be, Capabilities.Item.BLOCK);
        ResourceHandler<FluidResource> fluids = cap(level, be, Capabilities.Fluid.BLOCK);
        boolean hasEnergy = energy != null && energy.getCapacityAsLong() > 0;
        boolean hasItems = items != null && items.size() > 0;
        boolean hasFluids = fluids != null && fluids.size() > 0;
        if (hasEnergy && (hasItems || hasFluids)) return true;
        if (activeProperty(state) != null && (hasEnergy || hasItems || hasFluids)) return true;
        if (hasItems && !hasEnergy && !hasFluids) {
            int slots = items.size();
            return slots >= 2 && slots <= Config.MAX_ITEM_ONLY_SLOTS.get();
        }
        if (hasFluids && hasItems) return items.size() <= Config.MAX_ITEM_ONLY_SLOTS.get();
        return false;
    }

    // ---------- sampling ----------

    public static MachineEntry sample(ServerLevel level, BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        long now = level.getGameTime();
        EnergyHandler energy = cap(level, be, Capabilities.Energy.BLOCK);
        ResourceHandler<ItemResource> items = cap(level, be, Capabilities.Item.BLOCK);
        ResourceHandler<FluidResource> fluids = cap(level, be, Capabilities.Fluid.BLOCK);

        long e = 0, em = 0, f = 0, fm = 0;
        int used = 0, slots = 0;
        long sig = 17;
        boolean anyFull = false;
        if (energy != null) {
            e = energy.getAmountAsLong();
            em = energy.getCapacityAsLong();
            sig = sig * 31 + e;
        }
        if (items != null) {
            slots = items.size();
            for (int i = 0; i < slots; i++) {
                ItemResource r = items.getResource(i);
                long amt = items.getAmountAsLong(i);
                if (r.isEmpty() || amt <= 0) continue;
                used++;
                sig = sig * 31 + i;
                sig = sig * 31 + System.identityHashCode(r.getItem());
                sig = sig * 31 + amt;
                long limit = Math.min(r.getMaxStackSize(), items.getCapacityAsLong(i, r));
                if (amt >= limit && canExtract(items, i, r)) anyFull = true;
            }
        }
        if (fluids != null) {
            for (int t = 0; t < fluids.size(); t++) {
                FluidResource fr = fluids.getResource(t);
                long amt = fluids.getAmountAsLong(t);
                long capT = fluids.getCapacityAsLong(t, fr);
                f += amt;
                fm += capT;
                sig = sig * 31 + amt;
                if (capT > 0 && amt >= capT) anyFull = true;
            }
        }
        Boolean active = null;
        BooleanProperty prop = activeProperty(state);
        if (prop != null) active = state.getValue(prop);
        Create.Info kin = Create.info(be);
        if (kin != null) sig = sig * 31 + Float.floatToIntBits(kin.speed());

        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        History h = HISTORY.get(gp);
        if (h == null) h = new History(sig, Long.MIN_VALUE / 2, now, now);
        else if (h.sig != sig) h = new History(sig, now, h.firstSeen, now);
        else h = new History(sig, h.lastChange, h.firstSeen, now);
        HISTORY.put(gp, h);
        prune(now);

        long idleTicks = Config.IDLE_SECONDS.get() * 20L;
        boolean changing = now - h.lastChange <= idleTicks;
        boolean young = now - h.firstSeen < idleTicks;

        MachineStatus status;
        if (kin != null && (kin.speed() == 0 || kin.overStressed())) {
            status = MachineStatus.NO_POWER;
        } else if (em > 0 && e == 0) {
            status = MachineStatus.NO_POWER;
        } else if (Boolean.TRUE.equals(active) || changing) {
            status = MachineStatus.WORKING;
        } else if (be instanceof AbstractFurnaceBlockEntity && items != null && slots >= 2
                && items.getAmountAsLong(1) == 0 && items.getAmountAsLong(0) > 0) {
            status = MachineStatus.NO_POWER; // furnace out of fuel
        } else if (young && active == null) {
            status = MachineStatus.CHECKING;
        } else if (anyFull) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            status = MachineStatus.IDLE;
        }
        return new MachineEntry(level.dimension().identifier(), pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), status,
                e, em, f, fm, used, slots, false);
    }

    private static boolean canExtract(ResourceHandler<ItemResource> h, int slot, ItemResource r) {
        try (Transaction tx = Transaction.openRoot()) {
            return h.extract(slot, r, 1, tx) > 0; // not committed, rolls back
        }
    }

    private static void prune(long now) {
        if (now - lastPrune < 1200) return;
        lastPrune = now;
        HISTORY.values().removeIf(h -> now - h.lastSeen > 1200);
    }

    public static void clear() {
        HISTORY.clear();
    }

    @Nullable
    private static BooleanProperty activeProperty(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof BooleanProperty bp && ACTIVE_PROPS.contains(p.getName())) return bp;
        }
        return null;
    }

    /** Look up a capability, trying the null side first and then each face. */
    @Nullable
    private static <T> T cap(Level level, BlockEntity be, BlockCapability<T, Direction> cap) {
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        T t = level.getCapability(cap, pos, state, be, null);
        if (t != null) return t;
        for (Direction d : Direction.values()) {
            t = level.getCapability(cap, pos, state, be, d);
            if (t != null) return t;
        }
        return null;
    }

    /** Create support via reflection so there's no hard dependency. */
    static final class Create {
        record Info(float speed, boolean overStressed) {}

        private static boolean tried;
        private static Class<?> kinetic;
        private static Method getSpeed, isOverStressed;

        private static void init() {
            if (tried) return;
            tried = true;
            try {
                kinetic = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
                getSpeed = kinetic.getMethod("getSpeed");
                isOverStressed = kinetic.getMethod("isOverStressed");
            } catch (Throwable t) {
                kinetic = null;
            }
        }

        static boolean isKinetic(BlockEntity be) {
            init();
            return kinetic != null && kinetic.isInstance(be);
        }

        @Nullable
        static Info info(BlockEntity be) {
            if (!isKinetic(be)) return null;
            try {
                return new Info((float) getSpeed.invoke(be), (boolean) isOverStressed.invoke(be));
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public static ChunkPos chunk(BlockPos p) {
        return ChunkPos.containing(p);
    }
}
