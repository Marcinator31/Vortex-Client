package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Forge RenderLevelStageEvent bridge for Vortex 3D world renderers. */
public final class LevelRenderEvents {
    public static final Event START_MAIN = new Event(RenderLevelStageEvent.Stage.AFTER_SKY);
    public static final Event AFTER_TRANSLUCENT_FEATURES = new Event(RenderLevelStageEvent.Stage.AFTER_PARTICLES);
    private LevelRenderEvents() {}
    @FunctionalInterface public interface Callback { void render(LevelRenderContext context); }
    public static final class Event {
        private final List<Callback> callbacks = new CopyOnWriteArrayList<>();
        private final RenderLevelStageEvent.Stage stage;
        private Event(RenderLevelStageEvent.Stage stage) {
            this.stage = stage;
            MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
                if (event.getStage() != this.stage) return;
                Minecraft client = Minecraft.getInstance();
                MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
                LevelRenderContext context = new LevelRenderContext(event.getPoseStack(), event.getCamera(), buffers, event.getPartialTick());
                for (Callback callback : callbacks) callback.render(context);
            });
        }
        public void register(Callback callback) { callbacks.add(callback); }
    }
}
