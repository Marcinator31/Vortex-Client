package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Ein Knopf der Vortex-Leiste im Haupt- und Pausenmenue: dunkle Flaeche,
 * Text links, Pixel-Symbol rechts. Schmale Fenster zeigen nur das Symbol
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

    /** Zeichnet den ganzen Knopf selbst (kein Vanilla-Sprite). */
    void zeichnen(GuiGraphicsExtractor g) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHovered() || isFocused();
        // Rahmen, Flaeche, beim Zeigen ein Akzentstrich links
        g.fill(x, y, x + w, y + h, 0xFF050408);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? VortexStyle.HOV : VortexStyle.CARD);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hover ? 0x33FFFFFF : 0x18FFFFFF);
        if (hover) g.fill(x + 1, y + 2, x + 3, y + h - 1, VortexStyle.VIOLETT);

        int farbe = hover ? 0xFFFFFFFF : VortexStyle.TEXT;
        int sw = MenuSymbole.breite(symbol), sh = MenuSymbole.hoehe(symbol);
        if (kompakt) {
            MenuSymbole.zeichnen(g, symbol, x + (w - sw) / 2, y + (h - sh) / 2, farbe);
            return;
        }
        var font = Minecraft.getInstance().font;
        g.text(font, getMessage(), x + 8, y + (h - 8) / 2, farbe, true);
        MenuSymbole.zeichnen(g, symbol, x + w - sw - 8, y + (h - sh) / 2, farbe);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
