package com.vortex.legacy.module.hud;

import com.vortex.legacy.hud.HudModule;

/** HUD-Element, das nur einen Text in einem Kasten zeigt. */
public abstract class SimpleText extends HudModule {
    private float w = 40, h = 17;

    protected SimpleText(String name, String description, int x, int y) { super(name, description, x, y); }

    /** Text (editor: Beispiel, wenn es gerade nichts gibt). null = nichts zeigen. */
    protected abstract String text(boolean editor);

    @Override public float width() { return w; }
    @Override public float height() { return h; }

    @Override
    public void render(boolean editor) {
        String s = text(editor);
        if (s == null) return;
        w = textBoxWidth(s);
        h = textBoxHeight();
        drawTextBox(s);
    }
}
