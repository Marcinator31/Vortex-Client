package com.vortex.client.musik;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.gui.glatt.Symbole.Symbol;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NowPlayingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * HUD "Now Playing": Karte mit Cover, Titel, Kuenstler und Fortschritt.
 * Wenige Zeichenbefehle (Glatt-Formen), gekuerzte Texte werden gemerkt --
 * nicht jedes Bild neu vermessen.
 */
public final class MusikHud {
    private MusikHud() {}

    private static String merkTitel, merkTitelKurz, merkUnter, merkUnterKurz;
    private static float sichtbar;          // 0..1, weiches Ein-/Ausblenden
    private static long zuletzt;

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        NowPlayingModule m = ModuleManager.INSTANCE.get(NowPlayingModule.class);
        if (m == null || !m.isEnabled()) return;
        boolean editor = mc.gui.screen() instanceof com.vortex.client.gui.HudEditorScreen;
        Song s = MusikDienst.eigener();
        String unter = null;
        if (m.showListening.get() && MusikDienst.ziel() != null) unter = "with " + MusikDienst.zielName();
        if (s == null && editor) s = new Song("", "Song title", "Artist", "", "", 200_000, 64_000, true, System.currentTimeMillis(), "");
        boolean zeigen = s != null && (s.spielt() || !m.hidePaused.get() || editor);

        long jetzt = System.currentTimeMillis();
        float dt = zuletzt == 0 ? 0 : Math.min(0.1f, (jetzt - zuletzt) / 1000f);
        zuletzt = jetzt;
        sichtbar += ((zeigen ? 1f : 0f) - sichtbar) * (1f - (float) Math.exp(-dt * 10f));
        if (sichtbar < 0.02f || s == null) return;
        float a = sichtbar;

        float sc = (float) m.scale.get();
        var p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(m.x.getInt(), m.y.getInt());
            p.scale(sc, sc);
            float w = NowPlayingModule.BREITE, h = NowPlayingModule.HOEHE;
            int akzent = m.color.get() | 0xFF000000;
            Glatt.schatten(ctx, 0, 1, w, h, 8, 5, Glatt.alpha(0xFF000000, a * 0.35f));
            Glatt.rund(ctx, 0, 0, w, h, 8, Glatt.alpha(0xE6100D18, a));
            Glatt.rahmen(ctx, 0, 0, w, h, 8, 1, Glatt.alpha(0x22FFFFFF, a));

            float tx = 8;
            if (m.cover.get()) {
                float c = h - 10;
                Identifier id = Cover.von(s.cover());
                if (id != null) {
                    ctx.blit(RenderPipelines.GUI_TEXTURED, id, 5, 5, 0f, 0f, (int) c, (int) c, 128, 128, 128, 128, Glatt.alpha(0xFFFFFFFF, a));
                } else {
                    Glatt.rund(ctx, 5, 5, c, c, 5, Glatt.alpha(Glatt.mix(0xFF1B1726, akzent, 0.25f), a));
                    Glatt.symbol(ctx, Symbol.NOTE, 5 + c / 2 - 8, 5 + c / 2 - 10, 16, Glatt.alpha(akzent, a));
                }
                // kleines Spotify-Abzeichen unten auf dem Cover
                Glatt.rund(ctx, 6, 5 + c - 9, c - 2, 8, 2, Glatt.alpha(0xC8000000, a));
                Glatt.symbol(ctx, Symbol.NOTE, 7.5f, 5 + c - 8, 6, Glatt.alpha(0xFF1ED760, a));
                p.pushMatrix();
                p.translate(14f, 5 + c - 7.6f);
                p.scale(0.62f, 0.62f);
                Glatt.text(ctx, "Spotify", 0, 0, Glatt.alpha(0xFFFFFFFF, a), Schrift.FETT);
                p.popMatrix();
                tx = 5 + c + 7;
            }
            float maxW = w - tx - 8;
            if (!s.titel().equals(merkTitel)) { merkTitel = s.titel(); merkTitelKurz = Glatt.kuerzen(s.titel(), (int) maxW, Schrift.FETT); }
            String u = unter != null ? s.kuenstler() + "  ·  " + unter : s.kuenstler();
            if (!u.equals(merkUnter)) { merkUnter = u; merkUnterKurz = Glatt.kuerzen(u, (int) maxW, Schrift.NORMAL); }
            Glatt.text(ctx, merkTitelKurz, tx, 6, Glatt.alpha(0xFFFFFFFF, a), Schrift.FETT);
            Glatt.text(ctx, merkUnterKurz, tx, 17, Glatt.alpha(0xFFB9B3CC, a), Schrift.NORMAL);
            if (!s.spielt()) Glatt.symbol(ctx, Symbol.PAUSE, w - 14, 6, 8, Glatt.alpha(0xFFB9B3CC, a));

            if (m.progress.get() && s.dauer() > 0) {
                float by = 32, bw = maxW;
                Glatt.rund(ctx, tx, by, bw, 2.5f, 1.25f, Glatt.alpha(0x33FFFFFF, a));
                Glatt.rund(ctx, tx, by, Math.max(2.5f, bw * s.anteil()), 2.5f, 1.25f, Glatt.alpha(akzent, a));
                // Zeiten unter dem Balken (kleiner)
                p.pushMatrix();
                p.translate(tx, by + 5);
                p.scale(0.78f, 0.78f);
                Glatt.text(ctx, Song.zeit(s.jetzt()), 0, 0, Glatt.alpha(0xFFB9B3CC, a), Schrift.NORMAL);
                Glatt.textRechts(ctx, Song.zeit(s.dauer()), bw / 0.78f, 0, Glatt.alpha(0xFFB9B3CC, a), Schrift.NORMAL);
                p.popMatrix();
            }
        } finally {
            p.popMatrix();
        }
    }
}
