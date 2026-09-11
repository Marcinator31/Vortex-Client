package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.net.URI;

/**
 * Points out the two mods that actually make the game faster.
 *
 * WHY THIS RATHER THAN SHIPPING THEM WITH US:
 *
 * Bundling Sodium is a licensing problem. It is under the PolyForm Shield
 * licence, which forbids using it in anything that competes with it -- and this
 * client has performance modules of its own. Whether that would hold up is not
 * for me to decide, but it is unclear enough not to do quietly.
 *
 * Lithium could be bundled legally. It still should not be: a copy frozen into
 * our releases goes stale within weeks, and players would run an outdated
 * version with our name attached to whatever it breaks.
 *
 * And writing jars into someone's mods folder at startup is the behaviour of
 * software nobody wants installed. It also would not take effect until the next
 * restart, so it would look broken the one time it mattered.
 *
 * A link that is always current, that the player follows if they want, does the
 * job without any of that.
 */
public final class PerformanceHint {

    private static final String SODIUM_URL = "https://modrinth.com/mod/sodium";
    private static final String LITHIUM_URL = "https://modrinth.com/mod/lithium";

    /** Shown once per game start, not once per world join. */
    private static boolean shown = false;

    private PerformanceHint() {}

    /** Called after joining a world. */
    public static void maybeShow(Minecraft client) {
        try {
            if (shown) return;
            if (client == null || client.player == null) return;

            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.PotatoModeModule.class);
            if (mod != null && !mod.suggestMods.get()) {
                shown = true;
                return;
            }

            boolean sodium = isLoaded("sodium");
            boolean lithium = isLoaded("lithium");
            if (sodium && lithium) {
                shown = true;
                return;
            }

            shown = true;
            client.player.sendSystemMessage(Component.literal("§b[Vortex] §7For more frames:"));

            if (!sodium) {
                client.player.sendSystemMessage(link("Sodium",
                        "rewrites the rendering engine — the big one", SODIUM_URL));
            }
            if (!lithium) {
                client.player.sendSystemMessage(link("Lithium",
                        "speeds up the game logic", LITHIUM_URL));
            }
            client.player.sendSystemMessage(
                    Component.literal("§8Click a name to open it. Turn this off in Potato Mode."));
        } catch (Throwable pvpErr) {
            Errors.report("PerformanceHint", pvpErr);
        }
    }

    /** A clickable line. */
    private static MutableComponent link(String name, String what, String url) {
        MutableComponent text = Component.literal("  §b" + name + " §7- " + what);
        try {
            return text.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                    .withUnderlined(Boolean.TRUE));
        } catch (Throwable pvpErr) {
            // Without the link the line still says what to look for.
            Errors.report("PerformanceHint.link", pvpErr);
            return text;
        }
    }

    private static boolean isLoaded(String id) {
        try {
            return FabricLoader.getInstance().isModLoaded(id);
        } catch (Throwable pvpErr) {
            // In doubt, assume it is there and stay quiet -- a wrong nag is
            // worse than a missing one.
            return true;
        }
    }
}
