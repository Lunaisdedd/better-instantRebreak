package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class ModuleExample extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between break attempts.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> pick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-pick")
        .description("Only mine if holding a pickaxe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderBlocks = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the blocks being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the blocks.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    private int ticks = 0;

    // Multi-block support
    private final List<BlockPos> targetBlocks = new ArrayList<>();
    private Direction direction;

    public ModuleExample() {
        super(AddonTemplate.CATEGORY, "multi-instant-rebreak", "Instantly re-breaks multiple blocks.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        targetBlocks.clear();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        direction = event.direction;
        if (!targetBlocks.contains(event.blockPos)) targetBlocks.add(event.blockPos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticks >= tickDelay.get()) {
            ticks = 0;
            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos pos : targetBlocks) {
                if (shouldMine(pos)) {
                    if (rotate.get()) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), this::sendPacket);
                    else sendPacket(pos);

                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                } else {
                    toRemove.add(pos);
                }
            }
            targetBlocks.removeAll(toRemove);
        } else ticks++;
    }

    private void sendPacket(BlockPos pos) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction == null ? Direction.UP : direction));
    }

    private boolean shouldMine(BlockPos pos) {
        if (mc.world.isOutOfHeightLimit(pos) || !BlockUtils.canBreak(pos)) return false;
        return !pick.get() || mc.player.getMainHandStack().isIn(ItemTags.PICKAXES);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderBlocks.get()) return;
        for (BlockPos pos : targetBlocks) {
            event.renderer.box(pos, color.get(), color.get(), ShapeMode.Both, 0);
        }
    }
}
