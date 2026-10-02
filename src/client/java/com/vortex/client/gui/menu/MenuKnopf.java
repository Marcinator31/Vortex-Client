package com.vortex.client.gui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Ein Knopf der Vortex-Leiste im Haupt- und Pausenmenue: glatte Glasflaeche,
 * Text links, Symbol rechts. Schmale Fenster zeigen nur das Symbol
 * (der Name kommt dann als Tooltip).
 */
public class MenuKnopf extends AbstractButton {
    private final String[] symbol;
    private final Runnable aktion;
    private final boolean kompakt;

    public MenuKnopf(int x, int y, int w, int h, String text, String[] symbol, boolean kompakt, Runnable aktion) {
        super(x, y, w, h, Component.literal(text));
        this.symbol = symbol;
        this.aktion = aktion;
        this.kompakt = kompakt;
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
     * Zeichnet den ganzen Knopf selbst (kein Vanilla-Sprite). Seit 4.17 glatt:
     * runde Glasflaeche, Vektor-Symbol und Schrift "Inter" statt Pixeln.
     */
    void zeichnen(GuiGraphicsExtractor g) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float hv = com.vortex.client.gui.glatt.MenuStil.grund(g, this);
        float alpha = Math.max(0f, Math.min(1f, getAlpha()));
        int farbe = com.vortex.client.gui.glatt.Glatt.mix(0xFFD6D1E6, 0xFFFFFFFF, hv);
        farbe = com.vortex.client.gui.glatt.Glatt.alpha(farbe, alpha);
        var sym = symbolFuer(symbol);
        float sg = 12;
        if (kompakt) {
            com.vortex.client.gui.glatt.Glatt.symbol(g, sym, x + (w - sg) / 2f, y + (h - sg) / 2f, sg, farbe);
            return;
        }
        com.vortex.client.gui.glatt.Glatt.text(g, getMessage().getString(), x + 10, y + (h - 9) / 2f + 0.5f, farbe,
                com.vortex.client.gui.glatt.Glatt.Schrift.FETT);
        com.vortex.client.gui.glatt.Glatt.symbol(g, sym, x + w - sg - 9, y + (h - sg) / 2f, sg, farbe);
    }

    private static com.vortex.client.gui.glatt.Symbole.Symbol symbolFuer(String[] s) {
        if (s == MenuSymbole.HOST) return com.vortex.client.gui.glatt.Symbole.Symbol.WELLEN;
        if (s == MenuSymbole.SOCIAL) return com.vortex.client.gui.glatt.Symbole.Symbol.LEUTE;
        if (s == MenuSymbole.WARDROBE) return com.vortex.client.gui.glatt.Symbole.Symbol.HEMD;
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
