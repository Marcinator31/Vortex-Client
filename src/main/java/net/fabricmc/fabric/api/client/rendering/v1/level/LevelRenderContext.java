package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/** Minimal 1.20.1 world-render context consumed by existing Vortex renderers. */
public final class LevelRenderContext {
    private final PoseStack poseStack;
    private final Camera camera;
    private final MultiBufferSource bufferSource;
    private final float tickDelta;
    public LevelRenderContext(PoseStack poseStack, Camera camera, MultiBufferSource bufferSource, float tickDelta) {
        this.poseStack = poseStack; this.camera = camera; this.bufferSource = bufferSource; this.tickDelta = tickDelta;
    }
    public PoseStack poseStack() { return poseStack; }
    public Camera camera() { return camera; }
    public MultiBufferSource bufferSource() { return bufferSource; }
    public float tickDelta() { return tickDelta; }
}
