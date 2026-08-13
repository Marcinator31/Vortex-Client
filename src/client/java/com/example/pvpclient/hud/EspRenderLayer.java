package com.example.pvpclient.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

/**
 * Stellt einen Linien-RenderLayer bereit, der OHNE Tiefentest zeichnet -- die
 * Linien sind dadurch durch Waende sichtbar (typisches ESP-Verhalten).
 *
 * In 1.21.11 wird das ueber eine eigene RenderPipeline gesteuert: wir kopieren
 * die normale Linien-Pipeline (RENDERTYPE_LINES_SNIPPET), schalten aber den
 * Tiefentest auf NO_DEPTH_TEST und das Culling aus. Daraus bauen wir per
 * RenderSetup einen RenderLayer.
 *
 * Alle Namen gegen die echten 1.21.11-Yarn-Mappings (build.4) geprueft.
 */
public final class EspRenderLayer {

    private EspRenderLayer() {}

    // Eigene no-depth Linien-Pipeline (einmalig registriert).
    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("pvpclient", "pipeline/esp_lines"))
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    // Der fertige Layer auf Basis der no-depth Pipeline.
    private static final RenderLayer ESP_LINES = RenderLayer.of(
            "pvpclient_esp_lines",
            RenderSetup.builder(ESP_LINES_PIPELINE).build()
    );

    public static RenderLayer espLines() {
        return ESP_LINES;
    }
}
