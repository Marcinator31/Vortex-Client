package com.vortex.client.hud;

import net.minecraft.client.renderer.RenderType;

/**
 * Shared 1.20.1 line render type for Vortex ESP, tracer and waypoint geometry.
 * The world render callbacks retain their explicit depth-state handling around
 * the buffered draw; this class only supplies the common classic line format.
 */
public final class EspRenderLayer {
    private EspRenderLayer() {}
    public static RenderType espLines() { return RenderType.lines(); }
}
