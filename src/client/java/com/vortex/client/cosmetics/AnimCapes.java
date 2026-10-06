package com.vortex.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laufzeit der animierten Capes (CapeKunst): je Cape zwei Texturen (Basis +
 * Leuchten), die ein Hintergrund-Thread laufend neu berechnet. Hochgeladen
 * wird im Render-Thread, sobald jemand die Textur abfragt -- also nur, wenn
 * das Cape gerade irgendwo zu sehen ist (Spieler, Vorschau, Menue).
 *
 * Ein Cape, das 1,5 s nicht gefragt wurde, ruht (kein Rechnen, kein Hochladen).
 */
public final class AnimCapes {
    private AnimCapes() {}

    private static final class Platz {
        final CapeKunst.Design design;
        final Identifier basisId, glowId;
        DynamicTexture basis, glow;
        final int[] fertigB = new int[CapeKunst.W * CapeKunst.H], fertigG = new int[CapeKunst.W * CapeKunst.H];
        volatile boolean neu;
        volatile long gefragt;
        long hochgeladen;

        Platz(CapeKunst.Design d) {
            this.design = d;
            this.basisId = Identifier.fromNamespaceAndPath("vortexclient", "cape/anim/" + d.id());
            this.glowId = Identifier.fromNamespaceAndPath("vortexclient", "cape/anim/" + d.id() + "_glow");
        }
    }

    private static final Map<String, Platz> PLAETZE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Platz> NACH_TEXTUR = new ConcurrentHashMap<>();
    private static final long START = System.nanoTime();
    private static volatile Thread maler;
    private static volatile boolean kaputt;

    /** Ist das ein animiertes Vortex-Cape? */
    public static boolean ist(String id) {
        return CapeKunst.design(id) != null;
    }

    static float zeit() {
        return (System.nanoTime() - START) / 1e9f;
    }

    /** Basistextur des Capes (Render-Thread). Laedt neue Bilder hoch, wenn welche fertig sind. */
    public static Identifier basis(String id) {
        if (kaputt) return null;
        CapeKunst.Design d = CapeKunst.design(id);
        if (d == null) return null;
        Platz p = PLAETZE.get(id);
        try {
            if (p == null) p = anlegen(d);
            p.gefragt = System.currentTimeMillis();
            pumpe(p);
            return p.basisId;
        } catch (Throwable t) {
            kaputt = true;
            com.vortex.client.core.Errors.report("AnimCapes.basis", t);
            return null;
        }
    }

    /** Leucht-Textur zu einer Basistextur (oder null, wenn es kein animiertes Cape ist). */
    public static Identifier glowZu(Identifier basis) {
        Platz p = basis == null ? null : NACH_TEXTUR.get(basis);
        if (p == null) return null;
        p.gefragt = System.currentTimeMillis();
        pumpe(p);
        return p.glowId;
    }

    /** Fuer Vorschaubilder im Menue: Leucht-Textur nach Cape-Id. */
    public static Identifier glow(String id) {
        Identifier b = basis(id);
        return b == null ? null : glowZu(b);
    }

    private static synchronized Platz anlegen(CapeKunst.Design d) {
        Platz p = PLAETZE.get(d.id());
        if (p != null) return p;
        p = new Platz(d);
        // Erstes Bild sofort (sonst waere das Cape einen Moment leer)
        CapeKunst.rendern(d, zeit(), p.fertigB, p.fertigG);
        NativeImage b = new NativeImage(CapeKunst.W, CapeKunst.H, false);
        NativeImage g = new NativeImage(CapeKunst.W, CapeKunst.H, false);
        kopiere(p.fertigB, b);
        kopiere(p.fertigG, g);
        p.basis = new DynamicTexture(() -> "vortexclient-anim-cape", b);
        p.glow = new DynamicTexture(() -> "vortexclient-anim-cape-glow", g);
        var tm = Minecraft.getInstance().getTextureManager();
        tm.register(p.basisId, p.basis);
        tm.register(p.glowId, p.glow);
        PLAETZE.put(d.id(), p);
        NACH_TEXTUR.put(p.basisId, p);
        starteMaler();
        return p;
    }

    private static void kopiere(int[] quelle, NativeImage ziel) {
        MemoryUtil.memIntBuffer(ziel.getPointer(), quelle.length).put(0, quelle);
    }

    /** Fertiges Bild hochladen (hoechstens ~30-mal je Sekunde). */
    private static void pumpe(Platz p) {
        if (!p.neu) return;
        long jetzt = System.nanoTime();
        if (jetzt - p.hochgeladen < 30_000_000L) return;
        synchronized (p) {
            kopiere(p.fertigB, p.basis.getPixels());
            kopiere(p.fertigG, p.glow.getPixels());
            p.neu = false;
        }
        p.basis.upload();
        p.glow.upload();
        p.hochgeladen = jetzt;
    }

    private static synchronized void starteMaler() {
        if (maler != null) return;
        Thread t = new Thread(AnimCapes::malen, "Vortex-Animated-Capes");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        maler = t;
        t.start();
    }

    /** Hintergrund: alle gerade sichtbaren Capes laufend neu rechnen (Ziel 24 Bilder/s). */
    private static void malen() {
        int[] b = new int[CapeKunst.W * CapeKunst.H], g = new int[CapeKunst.W * CapeKunst.H];
        long abstand = 41_000_000L;
        while (true) {
            long start = System.nanoTime();
            long jetzt = System.currentTimeMillis();
            try {
                for (Platz p : PLAETZE.values()) {
                    if (jetzt - p.gefragt > 1500 || p.neu) continue;
                    CapeKunst.rendern(p.design, zeit(), b, g);
                    synchronized (p) {
                        System.arraycopy(b, 0, p.fertigB, 0, b.length);
                        System.arraycopy(g, 0, p.fertigG, 0, g.length);
                        p.neu = true;
                    }
                }
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("AnimCapes.malen", t);
            }
            long dauer = System.nanoTime() - start;
            // Langsamer Rechner: weniger Bilder statt Ruckeln
            abstand = dauer > 25_000_000L ? 66_000_000L : 41_000_000L;
            long rest = abstand - dauer;
            try { Thread.sleep(Math.max(5, rest / 1_000_000L)); } catch (InterruptedException e) { return; }
        }
    }
}
