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
public class NoRenderBlocksModule extends Module {

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
     * Asks the game to rebuild the visible chunks.
     *
     * Goes through the same accessor Potato Mode uses, because the renderer
     * field is not public. If it fails the change simply shows up a little
     * later, when the chunks are rebuilt for other reasons.
     */
    public static void rebuildChunks() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.world == null) return;
            var acc = (com.vortex.client.mixin.client.MinecraftClientAccessor) client;
            var wr = acc.pvpclient$getWorldRenderer();
            if (wr != null) wr.reload();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("NoRenderBlocks.rebuild", pvpErr);
        }
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
}
