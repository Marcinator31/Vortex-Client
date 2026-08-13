package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

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

    private static ArmorHudModule module() {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(ArmorHudModule.class);
    }

    public static void render(DrawContext context, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        ArmorHudModule mod = module();
        if (mod == null) return;

        ItemStack[] stacks = new ItemStack[] {
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET),
            player.getMainHandStack()
        };

        // Einzel-Offsets pro Teil (gleiche Reihenfolge wie stacks).
        int[][] offsets = new int[][] {
            { mod.helmetOffsetX.getInt(), mod.helmetOffsetY.getInt() },
            { mod.chestOffsetX.getInt(),  mod.chestOffsetY.getInt()  },
            { mod.legsOffsetX.getInt(),   mod.legsOffsetY.getInt()   },
            { mod.bootsOffsetX.getInt(),  mod.bootsOffsetY.getInt()  },
            { mod.handOffsetX.getInt(),   mod.handOffsetY.getInt()   }
        };

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

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
            context.drawItem(stack, x, y);

            // Der Vanilla-"drawStackOverlay" zeichnet u.a. den Haltbarkeits-
            // BALKEN (gruen->rot) und die Stapelgroesse. Den wollen wir nur im
            // Balken-Modus -- bei Prozent/Schlaege wuerde er doppelt zur Zahl
            // erscheinen. Die Stapelgroesse (z.B. bei Totem-Stacks) ist selten
            // relevant fuer Ruestung, daher ist das ok.
            if (mode.equals("Balken")) {
                context.drawStackOverlay(client.textRenderer, stack, x, y);
            }

            // Bei Prozent/Schlaege eine Zahl links neben das Icon schreiben.
            if (!mode.equals("Balken") && stack.isDamageable()) {
                int max = stack.getMaxDamage();
                int leftDur = max - stack.getDamage();

                String txt;
                if (mode.equals("Schlaege")) {
                    // Verbleibende Haltbarkeitspunkte als Zahl.
                    txt = Integer.toString(leftDur);
                } else {
                    // Prozent (Default).
                    int percent = (int) Math.round((leftDur / (double) max) * 100.0);
                    txt = percent + "%";
                }

                int textW = client.textRenderer.getWidth(txt);
                context.drawTextWithShadow(
                    client.textRenderer, txt,
                    x - textW - 4, y + (SLOT - 8) / 2, textColor
                );
            }
        }
    }
}
