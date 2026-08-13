package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ItemEspModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Item ESP: zeichnet Boxen um gedroppte Items (ItemEntities) und optional einen
 * Tracer dorthin.
 *
 * Entities sind ohnehin nur in der Render-Distanz geladen und die Iteration ist
 * billig, deshalb scannen wir hier direkt im Render-Thread (kein eigener
 * Worker noetig). Die Box bauen wir aus Position + Groesse des Items.
 */
public final class ItemEsp {

    private ItemEsp() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            ItemEspModule mod = (ItemEspModule) find(ItemEspModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = EspRender.cameraOffset(client, tickDelta);
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                int color = mod.getColor();
                if ((color >>> 24) == 0) color |= 0xFF000000;

                Vec3d start = EspRender.tracerStart(client, cam, tickDelta);
                org.joml.Matrix4f mat = matrices.peek().getPositionMatrix();
                boolean tracer = mod.tracerEnabled();

                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof ItemEntity)) continue;

                    // Box aus Position + Groesse (kein getBoundingBox noetig).
                    double w = e.getWidth() / 2.0;
                    double h = e.getHeight();
                    double ex = e.getX(), ey = e.getY(), ez = e.getZ();
                    Box box = new Box(ex - w, ey, ez - w, ex + w, ey + h, ez + w);
                    EspRender.drawBox(matrices, lines, box, cam, color, 1.5f);

                    if (tracer) {
                        Vec3d center = new Vec3d(ex, ey + h / 2.0, ez);
                        EspRender.drawTracer(mat, lines, start, center, cam, color, 1.5f);
                    }
                }
            } catch (Throwable pvpErr) {
                com.example.pvpclient.core.Errors.report("ItemEsp", pvpErr);
            }
                    } finally {
                com.example.pvpclient.core.Profiler.record("ItemEsp",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
