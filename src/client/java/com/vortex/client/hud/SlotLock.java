package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.SlotLockModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Logik der Slot-Sperre: Taste, Pruefung beim Wegwerfen, Schloss-Symbole.
 */
public final class SlotLock {

    private SlotLock() {}

    private static boolean tasteVorher = false;
    private static long meldungBis = 0;
    private static String meldung = "";

    private static SlotLockModule modul() {
        return ModuleManager.INSTANCE.get(SlotLockModule.class);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SlotLockModule m = modul();
            if (m == null || !m.isEnabled() || mc.player == null) {
                tasteVorher = false;
                return;
            }
            int code = m.lockKey.getKeyCode();
            boolean d = mc.gui.screen() == null && code != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
                    && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code);
            if (d && !tasteVorher) {
                int platz = mc.player.getInventory().getSelectedSlot();
                m.slots[platz].toggle();
                com.vortex.client.core.ConfigManager.save();
                zeige(m.slots[platz].get() ? "Slot " + (platz + 1) + " locked" : "Slot " + (platz + 1) + " unlocked");
            }
            tasteVorher = d;
        });
    }

    /** Q in der Hotbar: gesperrt? */
    public static boolean wegwerfenGesperrt(Player player) {
        SlotLockModule m = modul();
        if (m == null || player == null) return false;
        int platz = player.getInventory().getSelectedSlot();
        if (!m.gesperrt(platz)) return false;
        zeige("Slot " + (platz + 1) + " is locked");
        return true;
    }

    /** Wegwerfen aus einem Inventarfenster heraus: gesperrt? */
    public static boolean blockiert(int slotId, int button, ContainerInput input, Player player) {
        SlotLockModule m = modul();
        if (m == null || !m.isEnabled() || player == null) return false;
        if (input != ContainerInput.THROW) return false;
        var menu = player.containerMenu;
        if (menu == null || slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot s = menu.slots.get(slotId);
        if (s.container != player.getInventory()) return false;
        int platz = s.getContainerSlot();
        if (!m.gesperrt(platz)) return false;
        zeige("Slot " + (platz + 1) + " is locked");
        return true;
    }

    private static void zeige(String text) {
        meldung = text;
        meldungBis = System.currentTimeMillis() + 1500;
    }

    /** Schloesser ueber der Hotbar und kurze Meldung. */
    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        SlotLockModule m = modul();
        if (m == null || !m.isEnabled() || mc.player == null || mc.font == null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (m.showIcons.get()) {
            int x0 = sw / 2 - 91;
            for (int i = 0; i < 9; i++) {
                if (!m.slots[i].get()) continue;
                int x = x0 + i * 20 + 13;
                int y = sh - 22 - 1;
                // Kleines Schloss: Buegel und Koerper
                ctx.fill(x + 1, y, x + 5, y + 1, 0xFFFFD060);
                ctx.fill(x, y + 1, x + 1, y + 3, 0xFFFFD060);
                ctx.fill(x + 5, y + 1, x + 6, y + 3, 0xFFFFD060);
                ctx.fill(x - 1, y + 3, x + 7, y + 8, 0xFFFFD060);
                ctx.fill(x + 2, y + 4, x + 4, y + 6, 0xFF6B4E10);
            }
        }
        long rest = meldungBis - System.currentTimeMillis();
        if (rest > 0) {
            int alpha = (int) (255 * Math.min(1f, rest / 400f));
            int w = mc.font.width(meldung);
            ctx.text(mc.font, Component.literal(meldung), (sw - w) / 2, sh - 72,
                    (Math.max(8, alpha) << 24) | 0xFFD060);
        }
    }
}
