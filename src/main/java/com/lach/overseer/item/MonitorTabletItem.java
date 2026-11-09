package com.lach.overseer.item;

import com.lach.overseer.Registry;
import com.lach.overseer.machine.Machines;
import com.lach.overseer.server.Subscriptions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class MonitorTabletItem extends Item {
    public MonitorTabletItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.lach.overseer.client.ClientHooks.openTablet();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (be == null || !Machines.isMachine(level, be)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("overseer.msg.not_machine"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            boolean pinned = Subscriptions.togglePin(ctx.getItemInHand(), GlobalPos.of(level.dimension(), ctx.getClickedPos()));
            player.displayClientMessage(Component.translatable(pinned ? "overseer.msg.pinned" : "overseer.msg.unpinned"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int pins = stack.getOrDefault(Registry.PINS.get(), List.of()).size();
        tip.add(Component.translatable("item.overseer.monitor_tablet.tip1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.overseer.monitor_tablet.tip2").withStyle(ChatFormatting.GRAY));
        if (pins > 0) tip.add(Component.translatable("item.overseer.monitor_tablet.pins", pins).withStyle(ChatFormatting.GOLD));
    }
}
