package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitColorModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Faerbt das Treffer-Aufleuchten um (Modul Hit Color).
 *
 * Minecraft legt eine kleine Textur an: die obere Haelfte ist das Rot, das
 * ueber getroffene Wesen gelegt wird. Hier werden genau diese Pixel neu
 * gesetzt und die Textur neu hochgeladen -- einmal, wenn sich die Farbe
 * aendert, nicht in jedem Bild.
 *
 * Bewusst ueber Reflection: die Textur ist tief im Renderer versteckt, und
 * ihre Felder heissen in jeder Fassung ein wenig anders. Gesucht wird deshalb
 * nach dem TYP, nicht nach dem Namen. Findet sich nichts, bleibt das Modul
 * einfach wirkungslos -- es kann das Spiel nicht zum Absturz bringen.
 */
public final class HitColor {

    private HitColor() {}

    /** Vanilla-Rot mit der Vanilla-Deckkraft (ARGB). */
    private static final int VANILLA = 0xB2FF0000;

    private static int angewendet = VANILLA;
    private static boolean kaputt = false;
    private static int versuche = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (kaputt || mc.gameRenderer == null) return;
            HitColorModule m = ModuleManager.INSTANCE.get(HitColorModule.class);
            int ziel = (m != null && m.isEnabled()) ? m.color.get() : VANILLA;
            if (ziel == angewendet) return;
            if (setze(mc, ziel)) {
                angewendet = ziel;
            } else if (++versuche > 5) {
                kaputt = true;
                System.out.println("[vortexclient] Hit Color: Textur nicht gefunden, Modul inaktiv.");
            }
        });
    }

    private static boolean setze(Minecraft mc, int argb) {
        try {
            Object overlay = finde(mc.gameRenderer, "OverlayTexture");
            if (overlay == null) return false;
            Object dyn = finde(overlay, "DynamicTexture");
            if (dyn == null) return false;
            Object bild = dyn.getClass().getMethod("getPixels").invoke(dyn);
            if (bild == null) return false;

            Method setPixel = null;
            boolean abgr = false;
            for (Method me : bild.getClass().getMethods()) {
                if (me.getParameterCount() != 3) continue;
                Class<?>[] t = me.getParameterTypes();
                if (t[0] != int.class || t[1] != int.class || t[2] != int.class) continue;
                if (me.getName().equals("setPixel")) { setPixel = me; abgr = false; break; }
                if (me.getName().equals("setPixelABGR") || me.getName().equals("setPixelRGBA")) {
                    setPixel = me;
                    abgr = true;
                }
            }
            if (setPixel == null) return false;

            int wert = argb;
            if (abgr) {
                int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                wert = (a << 24) | (b << 16) | (g << 8) | r;
            }
            // Obere Haelfte (Zeilen 0..7) ist die Treffer-Farbe.
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) {
                    setPixel.invoke(bild, x, y, wert);
                }
            }
            dyn.getClass().getMethod("upload").invoke(dyn);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sucht in obj ein Feld oder eine Methode ohne Parameter, deren Typ so heisst. */
    private static Object finde(Object obj, String typName) throws Exception {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals(typName)) {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null) return v;
                }
            }
        }
        for (Method me : obj.getClass().getMethods()) {
            if (me.getParameterCount() == 0 && me.getReturnType().getSimpleName().equals(typName)) {
                Object v = me.invoke(obj);
                if (v != null) return v;
            }
        }
        return null;
    }
}
