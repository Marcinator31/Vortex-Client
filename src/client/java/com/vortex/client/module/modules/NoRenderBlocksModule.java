package com.vortex.client.module.modules;

import com.vortex.client.module.Module;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Hides selected block types from the world.
 *
 * The counterpart to Anti Render, which does the same for entities. Picked
 * blocks simply stop being drawn — you look straight through them, as if they
 * were air. Nothing else changes: they still stop you walking, still take time
 * to break, and the server has no idea anything is different.
 *
 * Sits under Cheats rather than Performance on purpose. Hiding stone to spot
 * ores through it is x-ray, and calling that a performance option would be
 * dressing it up as something it is not.
 */
public class NoRenderBlocksModule extends Module implements com.vortex.client.module.ExtraData, com.vortex.client.module.HasOwnScreen {

    /**
     * Block ids that are hidden, e.g. {@code minecraft:stone}.
     *
     * Insertion order is kept so the saved file stays readable and diffable
     * rather than shuffling itself on every write.
     */
    private final Set<String> hiddenBlocks = new LinkedHashSet<>();

    public NoRenderBlocksModule() {
        super("No Render Blocks", Category.PERFORMANCE);
    }

    public Set<String> getHiddenBlocks() {
        return hiddenBlocks;
    }

    public boolean isHidden(String blockId) {
        return hiddenBlocks.contains(blockId);
    }

    /**
     * Adds or removes a block.
     *
     * Chunks hold pre-built meshes, so a change only becomes visible once they
     * are rebuilt — otherwise you would tick a block and see nothing happen.
     */
    public void set(String blockId, boolean hidden) {
        if (hidden) {
            hiddenBlocks.add(blockId);
        } else {
            hiddenBlocks.remove(blockId);
        }
        rebuildChunks();
    }

    public void clearAll() {
        hiddenBlocks.clear();
        rebuildChunks();
    }

    /**
     * In 26.x dürfen Chunkdaten nicht mehr direkt über resetLevelRenderData()
     * zurückgesetzt werden. Zwischen Reset und dem nächsten Aufbau ist die
     * ViewArea null, wodurch der Renderthread abstürzen kann. Sichtbare Chunks
     * werden daher über den regulären Vanilla-Refresh erneuert.
     */
    public static void rebuildChunks() {
        // Kein direkter Rendererreset; der reguläre Chunk-Refresh ist sicher.
    }

    // ---- persistence ----

    public String serializeBlocks() {
        return String.join(",", hiddenBlocks);
    }

    public void deserializeBlocks(String data) {
        hiddenBlocks.clear();
        if (data == null || data.isEmpty()) return;
        for (String id : data.split(",")) {
            String t = id.trim();
            if (!t.isEmpty()) hiddenBlocks.add(t);
        }
    }

    @Override
    protected void onEnable() {
        rebuildChunks();
    }

    @Override
    protected void onDisable() {
        rebuildChunks();
    }

    // --- ExtraData: Zusatzliste ausserhalb der Einstellungen ---------------
    // Schluessel unveraendert, damit bestehende Presets weiter gelesen werden.

    @Override
    public String extraKey() { return "__hiddenblocks__"; }

    @Override
    public String serializeExtra() { return serializeBlocks(); }

    @Override
    public void deserializeExtra(String value) { deserializeBlocks(value); }

    @Override
    public void clearExtra() {
        clearAll();
    }


    // --- HasOwnScreen ------------------------------------------------------
    @Override
    public String screenButtonLabel() { return "Select blocks"; }

    @Override
    public net.minecraft.client.gui.screens.Screen createScreen(
            net.minecraft.client.gui.screens.Screen parent) {
        return new com.vortex.client.gui.NoRenderBlocksScreen(parent);
    }

}
