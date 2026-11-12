package com.lach.ioverseer.machine;

import com.lach.ioverseer.Config;
import com.lach.ioverseer.IOverseer;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

/** Finds machines and works out their status from NeoForge capabilities. Server side only. */
public final class Machines {
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, IOverseer.id("ignored"));
    public static final TagKey<Block> FORCED = TagKey.create(Registries.BLOCK, IOverseer.id("machines"));
    /** Block id words that mean energy storage or transport, not a machine. Add others to the ioverseer:ignored tag. */
    private static final List<String> STORAGE_WORDS = List.of("energy_cell", "energy_cube", "battery", "capacitor", "cable",
            "conduit", "wire", "accumulator", "induction", "energy_storage", "flux_point", "flux_plug", "flux_controller", "transmitter",
            "chemical_tank", "fluid_tank", "source_jar", "relay", "depot", "vault", "chute", "funnel", "tunnel", "belt");
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
            return new MachineEntry(dim.location(), pos, BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR),
                    MachineStatus.UNLOADED, 0, 0, 0, 0, 0, 0, true, List.of());
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
        if (isEnergyStorage(state)) return false; // batteries, cells, cables, tanks, jars, belts
        if (Create.isKinetic(be)) {
            Create.Info k = Create.info(be);
            if (k != null && k.impact() > 0) return true; // anything that draws stress: press, mixer, saw, fan, drill
            return cap(level, be, Capabilities.ItemHandler.BLOCK) != null || cap(level, be, Capabilities.FluidHandler.BLOCK) != null;
        }
        IEnergyStorage energy = cap(level, be, Capabilities.EnergyStorage.BLOCK);
        IItemHandler items = cap(level, be, Capabilities.ItemHandler.BLOCK);
        IFluidHandler fluids = cap(level, be, Capabilities.FluidHandler.BLOCK);
        boolean hasEnergy = energy != null && energy.getMaxEnergyStored() > 0;
        boolean hasItems = items != null && items.getSlots() > 0;
        boolean hasFluids = fluids != null && fluids.getTanks() > 0;
        if (isEnergyStorage(state)) return false; // batteries, cells, cables, tanks, jars
        if (Ars.isSourceTile(be) && hasItems) return true;
        if (hasEnergy && (hasItems || hasFluids)) return true;
        if (activeProperty(state) != null && (hasEnergy || hasItems || hasFluids)) return true;
        if (hasItems && !hasEnergy && !hasFluids) {
            int slots = items.getSlots();
            return slots >= 2 && slots <= Config.MAX_ITEM_ONLY_SLOTS.get();
        }
        if (hasFluids && hasItems) return items.getSlots() <= Config.MAX_ITEM_ONLY_SLOTS.get();
        return false;
    }

    // ---------- sampling ----------

    public static MachineEntry sample(ServerLevel level, BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        long now = level.getGameTime();
        IEnergyStorage energy = cap(level, be, Capabilities.EnergyStorage.BLOCK);
        IItemHandler items = cap(level, be, Capabilities.ItemHandler.BLOCK);
        IFluidHandler fluids = cap(level, be, Capabilities.FluidHandler.BLOCK);

        long e = 0, em = 0, f = 0, fm = 0;
        int used = 0, slots = 0;
        long sig = 17;
        boolean anyFull = false;
        if (energy != null) {
            e = energy.getEnergyStored();
            em = energy.getMaxEnergyStored();
            sig = sig * 31 + e;
        }
        if (items != null) {
            slots = items.getSlots();
            for (int i = 0; i < slots; i++) {
                ItemStack s = items.getStackInSlot(i);
                if (s.isEmpty()) continue;
                used++;
                sig = sig * 31 + i;
                sig = sig * 31 + System.identityHashCode(s.getItem());
                sig = sig * 31 + s.getCount();
                int limit = Math.min(s.getMaxStackSize(), items.getSlotLimit(i));
                if (s.getCount() >= limit) anyFull = true; // some mods hide extraction on the null side, so don't require it
            }
        }
        if (fluids != null) {
            for (int t = 0; t < fluids.getTanks(); t++) {
                FluidStack fs = fluids.getFluidInTank(t);
                int amt = fs.getAmount();
                int capT = fluids.getTankCapacity(t);
                f += amt;
                fm += capT;
                sig = sig * 31 + amt;
                if (capT > 0 && amt >= capT) anyFull = true;
            }
        }
        Create.Info kin = Create.info(be);
        List<Gauge> gauges = new ArrayList<>(Create.gauges(kin));
        gauges.addAll(Ars.gauges(be));
        gauges.addAll(MekChemicals.gauges(be));
        if (gauges.size() > 4) gauges = new ArrayList<>(gauges.subList(0, 4));
        long gaugeMax = 0, gaugeAmt = 0;
        for (Gauge g : gauges) {
            if (!"su".equals(g.unit())) sig = sig * 31 + g.amount();
            gaugeMax += g.max();
            gaugeAmt += g.amount();
        }
        boolean sourceOnly = em == 0 && !gauges.isEmpty() && "source".equals(gauges.get(0).unit());
        Boolean active = null;
        BooleanProperty prop = activeProperty(state);
        if (prop != null) active = state.getValue(prop);
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
        } else if (sourceOnly && gauges.get(0).max() > 0 && gauges.get(0).amount() == 0 && !changing) {
            status = MachineStatus.NO_POWER; // Ars machine with no Source
        } else if (Boolean.TRUE.equals(active) || changing || (kin != null && slots == 0 && fm == 0)) {
            status = MachineStatus.WORKING;
        } else if (be instanceof AbstractFurnaceBlockEntity && items != null && slots >= 2
                && items.getStackInSlot(1).isEmpty() && !items.getStackInSlot(0).isEmpty()) {
            status = MachineStatus.NO_POWER; // furnace out of fuel
        } else if (young && active == null) {
            status = MachineStatus.CHECKING;
        } else if (anyFull) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            status = MachineStatus.IDLE;
        }
        return new MachineEntry(level.dimension().location(), pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), status,
                e, em, f, fm, used, slots, false, gauges);
    }

    private static void prune(long now) {
        if (now - lastPrune < 1200) return;
        lastPrune = now;
        HISTORY.values().removeIf(h -> now - h.lastSeen > 1200);
    }

    public static void clear() {
        HISTORY.clear();
    }

    private static boolean isEnergyStorage(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        for (String w : STORAGE_WORDS) if (path.contains(w)) return true;
        return false;
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
    public static final class Create {
        /** speed in RPM, impact = SU per RPM this block applies, network stress / capacity in SU. */
        public record Info(float speed, boolean overStressed, float impact, float stress, float capacity, @Nullable Long network) {
            public long usage() {
                return (long) Math.abs(impact * speed);
            }
        }

        private static boolean tried;
        private static Class<?> kinetic;
        private static Method getSpeed, isOverStressed, stressApplied;
        private static java.lang.reflect.Field stressF, capacityF, networkF;

        private static void init() {
            if (tried) return;
            tried = true;
            try {
                kinetic = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
                getSpeed = kinetic.getMethod("getSpeed");
                isOverStressed = kinetic.getMethod("isOverStressed");
                stressApplied = kinetic.getMethod("calculateStressApplied");
                stressF = kinetic.getDeclaredField("stress");
                capacityF = kinetic.getDeclaredField("capacity");
                networkF = kinetic.getField("network");
                stressF.setAccessible(true);
                capacityF.setAccessible(true);
            } catch (Throwable t) {
                kinetic = null;
            }
        }

        static boolean isKinetic(BlockEntity be) {
            init();
            return kinetic != null && kinetic.isInstance(be);
        }

        @Nullable
        public static Info info(BlockEntity be) {
            if (!isKinetic(be)) return null;
            try {
                return new Info((float) getSpeed.invoke(be), (boolean) isOverStressed.invoke(be), (float) stressApplied.invoke(be),
                        stressF.getFloat(be), capacityF.getFloat(be), (Long) networkF.get(be));
            } catch (Throwable t) {
                return null;
            }
        }

        static List<Gauge> gauges(@Nullable Info k) {
            if (k == null) return List.of();
            List<Gauge> out = new ArrayList<>();
            out.add(new Gauge("ioverseer.gauge.speed", (long) Math.abs(k.speed()), 256, 0xFF3EC8E0, "rpm"));
            long cap = (long) k.capacity();
            if (cap > 0 || k.stress() > 0) {
                out.add(new Gauge("ioverseer.gauge.stress", (long) k.stress(), cap, k.overStressed() ? 0xFFE5484D : 0xFFE5A23C, "su"));
            }
            if (k.usage() > 0 && k.stress() > 0) out.add(new Gauge("ioverseer.gauge.su_used", k.usage(), (long) k.stress(), 0xFFB98C43, "su"));
            return out;
        }
    }

    // ---------- mod resources ----------

    /** Ars Nouveau Source via its ISourceTile interface, by reflection. */
    static final class Ars {
        private static boolean tried;
        private static Class<?> tile;
        private static Method get, max;

        private static void init() {
            if (tried) return;
            tried = true;
            try {
                tile = Class.forName("com.hollingsworth.arsnouveau.api.source.ISourceTile");
                get = tile.getMethod("getSource");
                max = tile.getMethod("getMaxSource");
            } catch (Throwable t) {
                tile = null;
            }
        }

        static boolean isSourceTile(BlockEntity be) {
            init();
            return tile != null && tile.isInstance(be);
        }

        static List<Gauge> gauges(BlockEntity be) {
            if (!isSourceTile(be)) return List.of();
            try {
                int m = (int) max.invoke(be);
                if (m <= 0) return List.of();
                return List.of(new Gauge("ioverseer.gauge.source", (int) get.invoke(be), m, 0xFFB45CE6, "source"));
            } catch (Throwable t) {
                return List.of();
            }
        }
    }

    /** Mekanism chemical tanks (gases, infuse types, pigments, slurries) via IMekanismChemicalHandler, by reflection. */
    static final class MekChemicals {
        private static boolean tried;
        private static Class<?> handler;
        private static Method tanks;

        private static void init() {
            if (tried) return;
            tried = true;
            try {
                handler = Class.forName("mekanism.api.chemical.IMekanismChemicalHandler");
                tanks = handler.getMethod("getChemicalTanks", Direction.class);
            } catch (Throwable t) {
                handler = null;
            }
        }

        static List<Gauge> gauges(BlockEntity be) {
            init();
            if (handler == null || !handler.isInstance(be)) return List.of();
            try {
                List<Gauge> out = new ArrayList<>();
                for (Object tank : (List<?>) tanks.invoke(be, (Object) null)) {
                    Class<?> tc = tank.getClass();
                    long cap = ((Number) tc.getMethod("getCapacity").invoke(tank)).longValue();
                    if (cap <= 0) continue;
                    long stored = ((Number) tc.getMethod("getStored").invoke(tank)).longValue();
                    String label = "ioverseer.gauge.chemical";
                    int color = 0xFF9AA0A8;
                    if (stored > 0) {
                        Object chem = tc.getMethod("getType").invoke(tank);
                        Object name = chem.getClass().getMethod("getTextComponent").invoke(chem);
                        if (name instanceof net.minecraft.network.chat.Component c
                                && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr) label = tr.getKey();
                        color = 0xFF000000 | ((Number) chem.getClass().getMethod("getTint").invoke(chem)).intValue();
                    }
                    out.add(new Gauge(label, stored, cap, color, "mB"));
                }
                return out;
            } catch (Throwable t) {
                return List.of();
            }
        }
    }

    // ---------- power draw ----------

    private record EnergyMark(long energy, long time, long draw) {}

    private static final Map<GlobalPos, EnergyMark> MARKS = new HashMap<>();

    /**
     * Estimated FE/t a working machine is using. Mekanism reports its own usage; for anything else it's the drop in
     * stored energy since the last call, which reads 0 while a cable keeps the buffer topped up. Generators (no
     * receive) count as 0.
     */
    public static long draw(ServerLevel level, MachineEntry e) {
        GlobalPos gp = GlobalPos.of(level.dimension(), e.pos());
        long now = level.getGameTime();
        EnergyMark prev = MARKS.get(gp);
        if (prev != null && prev.time == now) return prev.draw; // another player already read it this tick
        long d = drawNow(level, e, prev, now);
        if (MARKS.size() > 4096) MARKS.clear();
        MARKS.put(gp, new EnergyMark(e.energy(), now, d));
        return d;
    }

    private static long drawNow(ServerLevel level, MachineEntry e, @Nullable EnergyMark prev, long now) {
        if (e.status() != MachineStatus.WORKING || e.energyMax() <= 0) return 0;
        BlockEntity be = level.getBlockEntity(e.pos());
        if (be == null) return 0;
        IEnergyStorage energy = cap(level, be, Capabilities.EnergyStorage.BLOCK);
        if (energy == null || !energy.canReceive()) return 0;
        Long mek = Mekanism.usage(be, e.energyMax());
        if (mek != null) return mek;
        if (prev == null || now <= prev.time) return 0;
        return Math.max(0, prev.energy - e.energy()) / (now - prev.time);
    }

    /** Mekanism usage via reflection: sum of MachineEnergyContainer#getEnergyPerTick, scaled from joules to FE. */
    static final class Mekanism {
        private static final Map<Class<?>, Optional<Method>> CONTAINERS = new HashMap<>();

        @Nullable
        static Long usage(BlockEntity be, long feMax) {
            Method m = CONTAINERS.computeIfAbsent(be.getClass(), c -> {
                try {
                    return Optional.of(c.getMethod("getEnergyContainers", Direction.class));
                } catch (Throwable t) {
                    return Optional.empty();
                }
            }).orElse(null);
            if (m == null) return null;
            try {
                long perTick = 0, max = 0;
                boolean found = false;
                for (Object c : (List<?>) m.invoke(be, (Object) null)) {
                    Method pt, mx;
                    try {
                        pt = c.getClass().getMethod("getEnergyPerTick");
                        mx = c.getClass().getMethod("getMaxEnergy");
                    } catch (NoSuchMethodException ex) {
                        continue;
                    }
                    perTick += ((Number) pt.invoke(c)).longValue();
                    max += ((Number) mx.invoke(c)).longValue();
                    found = true;
                }
                if (!found) return null;
                double ratio = max > 0 && feMax > 0 ? (double) feMax / max : 1;
                return Math.round(perTick * ratio);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public static ChunkPos chunk(BlockPos p) {
        return new ChunkPos(p);
    }
}
