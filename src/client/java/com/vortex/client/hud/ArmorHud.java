package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * ArmorHUD -- Ruestungsteile + Waffe mit Haltbarkeit.
 *
 * Liest jetzt alle Settings aus dem ArmorHudModule:
 *  - Basis-Position (baseX/baseY) verschiebt die ganze HUD
 *  - Einzel-Offsets verschieben jedes Teil zusaetzlich
 *  - textColor faerbt die Prozentanzeige
 *  - durabilityMode waehlt die Haltbarkeits-Anzeige (Prozent/Schlaege/Balken)
 *
 * Hinweis zum Scale: Echtes Icon-Skalieren braucht die MatrixStack-API,
 * die in 1.21.11 nicht sicher verfuegbar ist. Daher wirkt 'scale' hier
 * auf den ABSTAND der Teile (groesserer Abstand = HUD wirkt groesser),
 * nicht auf die Icon-Pixelgroesse. Das ist der stabile Weg.
 */
public final class ArmorHud {

    private static final int SLOT = 16;
    private static final int PADDING = 2;

    /**
     * Reused offset array, so no new one is built every frame.
     *
     * Its contents only change when a setting is touched, but a fresh two
     * dimensional array was being built for every single frame.
     */
    private static final int[][] OFFSETS = new int[5][2];

    private static ArmorHudModule module() {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(ArmorHudModule.class);
    }

    public static void render(GuiGraphics context, Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        ArmorHudModule mod = module();
        if (mod == null) return;

        ItemStack[] stacks = new ItemStack[] {
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET),
            player.getMainHandItem()
        };

        // Offsets in ein wiederverwendetes Feld schreiben.
        //
        // Vorher entstand hier bei JEDEM Bild ein neues zweidimensionales Feld
        // -- bei 400 Bildern je Sekunde sind das 2400 Objekte, deren Inhalt
        // sich nur ändert, wenn du eine Einstellung anfasst.
        int[][] offsets = OFFSETS;
        int[][] werte = new int[][] {
            { mod.helmetOffsetX.getInt(), mod.helmetOffsetY.getInt() },
            { mod.chestOffsetX.getInt(),  mod.chestOffsetY.getInt()  },
            { mod.legsOffsetX.getInt(),   mod.legsOffsetY.getInt()   },
            { mod.bootsOffsetX.getInt(),  mod.bootsOffsetY.getInt()  },
            { mod.handOffsetX.getInt(),   mod.handOffsetY.getInt()   }
        };
        for (int i = 0; i < offsets.length; i++) {
            offsets[i][0] = werte[i][0];
            offsets[i][1] = werte[i][1];
        }

        int screenW = context.guiWidth();
        int screenH = context.guiHeight();

        // Abstand zwischen den Teilen, beeinflusst von 'scale'.
        double scale = mod.scale.get();
        int step = (int) Math.round((SLOT + PADDING) * scale);

        // Standard-Ankerpunkt: rechts mittig. Plus Basis-Offset aus den Settings.
        int totalHeight = stacks.length * step;
        int baseX = screenW - SLOT - 6 + mod.baseX.getInt();
        int startY = (screenH - totalHeight) / 2 + mod.baseY.getInt();

        String mode = mod.durabilityMode.get(); // "Prozent" / "Schlaege" / "Balken"
        int textColor = mod.textColor.get();

        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;

            int x = baseX + offsets[i][0];
            int y = startY + i * step + offsets[i][1];

            // Das Item-Icon immer zeichnen.
            context.renderItem(stack, x, y);

            // Der Vanilla-"drawStackOverlay" zeichnet u.a. den Haltbarkeits-
            // BALKEN (gruen->rot) und die Stapelgroesse. Den wollen wir nur im
            // Balken-Modus -- bei Prozent/Schlaege wuerde er doppelt zur Zahl
            // erscheinen. Die Stapelgroesse (z.B. bei Totem-Stacks) ist selten
            // relevant fuer Ruestung, daher ist das ok.
            if (mode.equals("Balken")) {
                context.renderItemDecorations(client.font, stack, x, y);
            }

            // Bei Prozent/Schlaege eine Zahl links neben das Icon schreiben.
            if (!mode.equals("Balken") && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int leftDur = max - stack.getDamageValue();

                String txt;
                if (mode.equals("Schlaege")) {
                    // Verbleibende Haltbarkeitspunkte als Zahl.
                    txt = Integer.toString(leftDur);
                } else {
                    // Prozent (Default).
                    int percent = (int) Math.round((leftDur / (double) max) * 100.0);
                    txt = percent + "%";
                }

                int textW = client.font.width(txt);
                context.drawString(
                    client.font, txt,
                    x - textW - 4, y + (SLOT - 8) / 2, textColor
                );
            }
        }
    }
}
