package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.Module;

/** Trefferboxen aller Wesen zeigen (wie F3+B, aber merkbar). */
public class Hitboxes extends Module {
    public Hitboxes() { super("Hitboxes", Category.PVP, "Shows entity hitboxes."); }
    @Override protected void onEnable() { mc.getEntityRenderManager().setRenderHitboxes(true); }
    @Override protected void onDisable() { mc.getEntityRenderManager().setRenderHitboxes(false); }
    @Override public void onTick() { if (!mc.getEntityRenderManager().getRenderHitboxes()) mc.getEntityRenderManager().setRenderHitboxes(true); }
}
