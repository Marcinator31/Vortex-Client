package com.vortex.client.cosmetics;

import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emote-Rad (Taste B): alle Emotes im Kreis, die Maus zeigt auf eines,
 * ein Klick spielt es. Esc oder ein Klick in die Mitte schliesst.
 */
public class EmoteRadScreen extends Screen {
    private final List<Emotes.Emote> liste = new ArrayList<>(Emotes.alle().values());
    private int gezeigt = -1;

    public EmoteRadScreen() {
        super(Component.literal("Emotes"));
    }

    private float radius() { return Math.min(this.width, this.height) * 0.32f; }

    /** Welches Emote liegt in Richtung der Maus? -1 = Mitte. */
    private int unterMaus(double mx, double my) {
        double dx = mx - this.width / 2.0, dy = my - this.height / 2.0;
        if (dx * dx + dy * dy < 26 * 26) return -1;
        double winkel = Math.atan2(dx, -dy);           // 0 = oben, im Uhrzeigersinn
        if (winkel < 0) winkel += Math.PI * 2;
        double stueck = Math.PI * 2 / liste.size();
        return (int) Math.floor((winkel + stueck / 2) / stueck) % liste.size();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x99060409);
        float cx = this.width / 2f, cy = this.height / 2f, r = radius();
        gezeigt = unterMaus(mouseX, mouseY);
        Glatt.kreis(g, cx, cy, r * 2 + 44, 0xC00E0B16);
        Glatt.kreis(g, cx, cy, 46, 0xF0181424);
        for (int i = 0; i < liste.size(); i++) {
            double w = Math.PI * 2 * i / liste.size();
            float x = cx + (float) Math.sin(w) * r, y = cy - (float) Math.cos(w) * r;
            boolean an = i == gezeigt;
            if (an) Glatt.licht(g, x, y, 34, Glatt.alpha(VortexStyle.VIOLETT, 0.45f));
            Glatt.kreis(g, x, y, an ? 40 : 34, an ? 0xFF2A1F4A : 0xF0181424);
            String name = liste.get(i).name();
            Glatt.textMitte(g, Glatt.kuerzen(name, 60, Glatt.Schrift.FETT), x, y - 4, an ? 0xFFFFFFFF : 0xFFC9C3DC, Glatt.Schrift.FETT);
        }
        String mitte = gezeigt >= 0 ? liste.get(gezeigt).name() : "Emotes";
        Glatt.textMitte(g, mitte, cx, cy - 8, 0xFFF2F0F8, Glatt.Schrift.FETT);
        Glatt.textMitte(g, gezeigt >= 0 ? "Click to play" : "Point at an emote", cx, cy + 4, 0xFF8D86A6, Glatt.Schrift.NORMAL);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int i = unterMaus(click.x(), click.y());
        onClose();
        if (i >= 0) Emotes.spielen(liste.get(i).id());
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
