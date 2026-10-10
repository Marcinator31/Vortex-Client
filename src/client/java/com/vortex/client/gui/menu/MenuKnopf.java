package com.vortex.client.gui.menu;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Ein Knopf der Vortex-Leiste im Haupt- und Pausenmenue (seit 4.30.2 als
 * Zeile im {@link MenuDock}): Symbol in einem kleinen Feld links, Name
 * daneben. Unter dem Zeiger leuchtet die Zeile in der Akzentfarbe auf, links
 * erscheint ein Balken und rechts ein Pfeil. Schmale Fenster zeigen nur das
 * Symbol (der Name kommt dann als Tooltip).
 */
public class MenuKnopf extends AbstractButton {
    private final String[] symbol;
    private final Runnable aktion;
    private final boolean kompakt;
    /** Nur beim ersten Knopf gesetzt: der zeichnet das Dock dahinter. */
    private final MenuDock dock;

    public MenuKnopf(int x, int y, int w, int h, String text, String[] symbol, boolean kompakt, Runnable aktion, MenuDock dock) {
        super(x, y, w, h, Component.literal(text));
        this.symbol = symbol;
        this.aktion = aktion;
        this.kompakt = kompakt;
        this.dock = dock;
        if (kompakt) setTooltip(Tooltip.create(Component.literal(text)));
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        try {
            aktion.run();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuKnopf", t);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        zeichnen(g);
    }

    /**
     * Zeichnet den ganzen Knopf selbst (kein Vanilla-Sprite): glatte Formen,
     * Vektor-Symbol und Schrift "Inter" statt Pixeln.
     */
    void zeichnen(GuiGraphicsExtractor g) {
        float alpha = Math.max(0f, Math.min(1f, getAlpha()));
        if (dock != null) dock.zeichnen(g, alpha);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float hv = MenuStil.hover(this, active && isHoveredOrFocused());
        int akz = MenuDock.akzent();
        int hell = Glatt.mix(akz, 0xFFFFFFFF, 0.4f);
        var sym = symbolFuer(symbol);

        // Zeile unter dem Zeiger
        if (hv > 0.01f) {
            float r = Math.min(7f, h / 2f);
            Glatt.rund(g, x, y, w, h, r, Glatt.alpha(Glatt.mix(0xFF16121F, akz, 0.32f), alpha * 0.9f * hv));
            Glatt.rahmen(g, x, y, w, h, r, 1, Glatt.alpha(hell, alpha * 0.35f * hv));
        }

        if (kompakt) {
            float sg = 12;
            Glatt.symbol(g, sym, x + (w - sg) / 2f, y + (h - sg) / 2f, sg,
                    Glatt.alpha(Glatt.mix(0xFFC9C3DC, 0xFFFFFFFF, hv), alpha));
            return;
        }

        // Akzentbalken links, waechst beim Ueberfahren aus der Mitte
        if (hv > 0.01f) {
            float bh = (h - 8) * hv;
            Glatt.rund(g, x + 1.5f, y + (h - bh) / 2f, 2f, bh, 1f, Glatt.alpha(hell, alpha * hv));
        }

        // Symbol in einem kleinen Feld
        float fg = 16, fx = x + 6, fy = y + (h - fg) / 2f;
        int feld = Glatt.mix(0x1AFFFFFF, Glatt.alpha(akz, 0.85f), hv);
        Glatt.rund(g, fx, fy, fg, fg, 5f, Glatt.alpha(feld, alpha));
        float sg = 10;
        Glatt.symbol(g, sym, fx + (fg - sg) / 2f, fy + (fg - sg) / 2f, sg,
                Glatt.alpha(Glatt.mix(0xFFC9C3DC, 0xFFFFFFFF, hv), alpha));

        // Name
        float tx = fx + fg + 7;
        int platz = (int) (x + w - tx - 12);
        String text = Glatt.kuerzen(getMessage().getString(), platz, Glatt.Schrift.FETT);
        Glatt.text(g, text, tx + hv, y + (h - 9) / 2f + 0.5f,
                Glatt.alpha(Glatt.mix(0xFFE4E0EE, 0xFFFFFFFF, hv), alpha), Glatt.Schrift.FETT);

        // Pfeil rechts, gleitet beim Ueberfahren herein
        if (hv > 0.01f) {
            float pg = 8;
            Glatt.symbol(g, com.vortex.client.gui.glatt.Symbole.Symbol.PFEIL_RECHTS,
                    x + w - pg - 6 - 3 * (1 - hv), y + (h - pg) / 2f, pg, Glatt.alpha(hell, alpha * hv));
        }
    }

    private static com.vortex.client.gui.glatt.Symbole.Symbol symbolFuer(String[] s) {
        if (s == MenuSymbole.HOST) return com.vortex.client.gui.glatt.Symbole.Symbol.WELLEN;
        if (s == MenuSymbole.SOCIAL) return com.vortex.client.gui.glatt.Symbole.Symbol.LEUTE;
        if (s == MenuSymbole.WARDROBE) return com.vortex.client.gui.glatt.Symbole.Symbol.HEMD;
        if (s == MenuSymbole.COSMETICS) return com.vortex.client.gui.glatt.Symbole.Symbol.STERN;
        if (s == MenuSymbole.PICTURES) return com.vortex.client.gui.glatt.Symbole.Symbol.BILD;
        if (s == MenuSymbole.SETTINGS) return com.vortex.client.gui.glatt.Symbole.Symbol.ZAHNRAD;
        if (s == MenuSymbole.ACCOUNT) return com.vortex.client.gui.glatt.Symbole.Symbol.PERSON;
        return com.vortex.client.gui.glatt.Symbole.Symbol.STERN;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
