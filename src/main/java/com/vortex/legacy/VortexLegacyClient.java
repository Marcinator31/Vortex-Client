package com.vortex.legacy;

import java.util.Arrays;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * Vortex Client port foundation for Legacy Fabric on Minecraft 1.13.2.
 *
 * <p>The state core is deliberately Java 8 compatible. Tick and HUD hooks are
 * mixin based, allowing the feature state to be reused by other historical
 * loader adapters later in the porting effort.</p>
 */
public final class VortexLegacyClient implements ModInitializer {
    public static final String MOD_ID = "vortexclient";
    private static final ClientState CLIENT_STATE = new ClientState();

    @Override
    public void onInitialize() {
        System.out.println("[Vortex Client] Legacy Fabric 1.13.2 port initialized.");
    }

    public static ClientState state() {
        return CLIENT_STATE;
    }

    /** First migrated client features with controls exposed in Minecraft's Controls menu. */
    public static final class ClientState {
        private static final String KEY_CATEGORY = "key.categories.vortexclient";

        private boolean hudEnabled = true;
        private boolean fullbrightEnabled;
        private boolean toggleSprintEnabled;
        private double savedBrightness = -1.0D;

        private KeyBinding hudKey;
        private KeyBinding fullbrightKey;
        private KeyBinding toggleSprintKey;

        public void onClientTick(MinecraftClient client) {
            if (client == null || client.options == null) {
                return;
            }

            registerKeyBindings(client.options);
            if (client.currentScreen == null) {
                while (hudKey.wasPressed()) {
                    hudEnabled = !hudEnabled;
                }
                while (fullbrightKey.wasPressed()) {
                    fullbrightEnabled = !fullbrightEnabled;
                }
                while (toggleSprintKey.wasPressed()) {
                    toggleSprintEnabled = !toggleSprintEnabled;
                }
            }

            applyFullbright(client.options);
            if (toggleSprintEnabled && client.player != null) {
                client.player.setSprinting(true);
            }
        }

        public boolean isHudEnabled() {
            return hudEnabled;
        }

        public boolean isFullbrightEnabled() {
            return fullbrightEnabled;
        }

        public boolean isToggleSprintEnabled() {
            return toggleSprintEnabled;
        }

        private void registerKeyBindings(GameOptions options) {
            if (hudKey != null) {
                return;
            }

            hudKey = new KeyBinding("key.vortexclient.hud", GLFW.GLFW_KEY_F6, KEY_CATEGORY);
            fullbrightKey = new KeyBinding("key.vortexclient.fullbright", GLFW.GLFW_KEY_F7, KEY_CATEGORY);
            toggleSprintKey = new KeyBinding("key.vortexclient.togglesprint", GLFW.GLFW_KEY_F8, KEY_CATEGORY);

            KeyBinding[] originalKeys = options.allKeys;
            KeyBinding[] vortexKeys = new KeyBinding[] { hudKey, fullbrightKey, toggleSprintKey };
            options.allKeys = Arrays.copyOf(originalKeys, originalKeys.length + vortexKeys.length);
            System.arraycopy(vortexKeys, 0, options.allKeys, originalKeys.length, vortexKeys.length);
            KeyBinding.updateKeysByCode();
        }

        private void applyFullbright(GameOptions options) {
            if (fullbrightEnabled) {
                if (savedBrightness < 0.0D) {
                    savedBrightness = options.method_18256(GameOptions.Option.BRIGHTNESS);
                }
                options.method_18257(GameOptions.Option.BRIGHTNESS, 1.0D);
            } else if (savedBrightness >= 0.0D) {
                options.method_18257(GameOptions.Option.BRIGHTNESS, savedBrightness);
                savedBrightness = -1.0D;
            }
        }
    }
}
