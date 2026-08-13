package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.module.Module;

/**
 * Steuert das Unterdruecken bestimmter Partikel (gegen Lag / fuer Sicht).
 *
 * Das eigentliche Abfangen passiert im ParticleManagerMixin, der dieses
 * Modul ausliest. Hier sind nur die Schalter:
 *  - Totem-Partikel (beim Totem-Pop)
 *  - Explosions-Partikel (Crystals, Anchors, TNT) -- spart FPS in Crystal-PvP
 *
 * Reine Optik, server-erlaubt.
 */
public class NoParticlesModule extends Module {

    public final BooleanSetting noTotem = new BooleanSetting("Keine Totem-Partikel", true);
    public final BooleanSetting noExplosion = new BooleanSetting("Keine Explosions-Partikel", true);

    public NoParticlesModule() {
        super("No Particles", Category.MISC);
        addSetting(noTotem);
        addSetting(noExplosion);
    }
}
