package com.lach.ioverseer;

import com.lach.ioverseer.block.MonitorScreenBlock;
import com.lach.ioverseer.block.MonitorScreenBlockEntity;
import com.lach.ioverseer.item.MonitorTabletItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class Registry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IOverseer.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IOverseer.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IOverseer.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, IOverseer.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IOverseer.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<GlobalPos>>> PINS = COMPONENTS.register("pins",
            () -> DataComponentType.<List<GlobalPos>>builder()
                    .persistent(GlobalPos.CODEC.listOf())
                    .networkSynchronized(GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    /** Live preview on the tablet's screen while held. Missing = on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PREVIEW = COMPONENTS.register("preview",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static final DeferredItem<MonitorTabletItem> TABLET = ITEMS.register("monitor_tablet",
            () -> new MonitorTabletItem(new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<MonitorScreenBlock> SCREEN = BLOCKS.register("monitor_screen",
            () -> new MonitorScreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 6)));
    public static final DeferredItem<BlockItem> SCREEN_ITEM = ITEMS.registerSimpleBlockItem(SCREEN);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorScreenBlockEntity>> SCREEN_BE = BES.register("monitor_screen",
            () -> BlockEntityType.Builder.of(MonitorScreenBlockEntity::new, SCREEN.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ioverseer"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> TABLET.get().getDefaultInstance())
            .displayItems((p, out) -> {
                out.accept(TABLET.get());
                out.accept(SCREEN_ITEM.get());
            })
            .build());

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BES.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
    }

    private Registry() {}
}
