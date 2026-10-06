package com.vortex.legacy.module;

import com.vortex.legacy.core.Errors;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;

/** Module, die in der Welt zeichnen (Chunk-Grenzen, Flugbahn ...). */
public final class WorldRenderers {
    private WorldRenderers() {}
    public interface InWorld { void renderWorld(float tickDelta); }

    public static void render(float tickDelta) {
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (!m.isEnabled() || !(m instanceof InWorld)) continue;
            try { ((InWorld) m).renderWorld(tickDelta); } catch (Throwable t) { Errors.report(m.getName() + ".world", t); }
        }
    }
}
