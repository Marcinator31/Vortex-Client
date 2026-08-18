package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Forge RenderGuiEvent bridge for Vortex HUD elements. */
public final class HudElementRegistry {
    private static final List<Callback> CALLBACKS = new CopyOnWriteArrayList<>();
    static {
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> {
            for (Callback callback : CALLBACKS) callback.render(event.getGuiGraphics(), null);
        });
    }
    private HudElementRegistry() {}
    public static void attachElementAfter(Object ignored, ResourceLocation id, Callback callback) { CALLBACKS.add(callback); }
    public static void removeElement(ResourceLocation id) { }
    @FunctionalInterface public interface Callback { void render(GuiGraphics graphics, Object tickCounter); }
}
