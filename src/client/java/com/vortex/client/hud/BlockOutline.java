package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BlockOutlineModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Zeichnet den Rahmen um den anvisierten Block (Modul Block Outline).
 *
 * Gleicher Weg wie die Hitboxen: ein Linien-Knoten im Welt-Rendering, mit
 * Tiefentest (hinter Bloecken verdeckt) oder ohne. Die Form kommt vom Block
 * selbst -- eine Treppe bekommt ihre Stufenkontur.
 */
public final class BlockOutline {

    private BlockOutline() {}

    public static boolean aktiv() {
        BlockOutlineModule m = ModuleManager.INSTANCE.get(BlockOutlineModule.class);
        return m != null && m.isEnabled();
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            BlockOutlineModule m = ModuleManager.INSTANCE.get(BlockOutlineModule.class);
            if (m == null || !m.isEnabled()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;

            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null) return;
            try {
                BlockPos pos = bhr.getBlockPos();
                VoxelShape form = mc.level.getBlockState(pos).getShape(mc.level, pos);
                if (form == null || form.isEmpty()) return;
                float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(mc, tickDelta);
                int farbe = m.color.get();
                if ((farbe >>> 24) == 0) farbe |= 0xFF000000;

                matrices.pushPose();
                try {
                    matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                    collector.submitShapeOutline(matrices, form,
                            m.throughWalls.get() ? EspRenderLayer.espLines() : EspRenderLayer.depthLines(),
                            farbe, m.width.getFloat(), true);
                } finally {
                    matrices.popPose();
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("BlockOutline", pvpErr);
            }
        });
    }
}
