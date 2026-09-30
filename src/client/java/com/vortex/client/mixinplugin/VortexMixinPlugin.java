package com.vortex.client.mixinplugin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Laesst einzelne Mixins weg, die sich mit anderen Mods nicht vertragen.
 *
 * Sodium ersetzt LevelRenderer.cullTerrain komplett (@Overwrite). Ein Mixin,
 * das IN diese Methode eingreift, laesst Minecraft schon beim Start abstuerzen
 * ("cannot inject into ... merged by ...sodium...LevelRendererMixin") -- auch
 * mit require = 0. Mit Sodium entscheidet ohnehin Sodium ueber das Culling.
 */
public final class VortexMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".FreecamSmartCullMixin")) {
            return !FabricLoader.getInstance().isModLoaded("sodium");
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
