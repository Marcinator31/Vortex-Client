package com.vortex.client.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Stellt einen Linien-RenderType bereit, der OHNE Tiefentest zeichnet -- die
 * Linien sind dadurch durch Waende sichtbar (typisches ESP-Verhalten).
 *
 * In 1.21.11 wird das ueber eine eigene RenderPipeline gesteuert: wir kopieren
 * die normale Linien-Pipeline (RENDERTYPE_LINES_SNIPPET), schalten aber den
 * Tiefentest auf NO_DEPTH_TEST und das Culling aus. Daraus bauen wir per
 * RenderSetup einen RenderType.
 *
 * Alle Namen gegen die echten 1.21.11-Yarn-Mappings (build.4) geprueft.
 */
public final class EspRenderLayer {

    private EspRenderLayer() {}

    // Eigene no-depth Linien-Pipeline (einmalig registriert).
    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("vortexclient", "pipeline/esp_lines"))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build()
    );

    // Der fertige Layer auf Basis der no-depth Pipeline.
    private static final RenderType ESP_LINES = RenderType.create(
            "vortexclient_esp_lines",
            RenderSetup.builder(ESP_LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType espLines() {
        return ESP_LINES;
    }
}
