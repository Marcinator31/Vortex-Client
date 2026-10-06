package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.gui.Render3D;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Eigener Rahmen um den angeschauten Block: Farbe, Dicke, Fuellung. */
public class BlockOutline extends Module {
    public final ColorSetting color = add(new ColorSetting("Outline Color", 0xCC8B5CF6));
    public final NumberSetting width = add(new NumberSetting("Thickness", 2.5, 1, 6, 0.5));
    public final BoolSetting fill = add(new BoolSetting("Fill", true));
    public final ColorSetting fillColor = add(new ColorSetting("Fill Color", 0x228B5CF6));

    public BlockOutline() { super("Block Outline", Category.VISUAL, "A custom outline around the block you look at."); }

    public void draw(BlockPos pos, float tickDelta) {
        Block block = mc.world.getBlockState(pos).getBlock();
        if (block.getMaterial() == Material.AIR || !mc.world.getWorldBorder().contains(pos)) return;
        block.setBoundingBox(mc.world, pos);
        Box b = block.getSelectionBox(mc.world, pos).expand(0.002, 0.002, 0.002);
        Render3D.setCamera(tickDelta);
        Render3D.begin(width.getFloat(), false);
        Render3D.box(b, fill.get() ? fillColor.get() : 0, color.get());
        Render3D.end();
    }
}
