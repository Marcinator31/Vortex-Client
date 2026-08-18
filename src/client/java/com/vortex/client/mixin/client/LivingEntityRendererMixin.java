package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.hud.TotemPops;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HealthIndicatorModule;
import com.vortex.client.module.modules.NametagModule;
import com.vortex.client.module.modules.TargetInfoModule;
import com.vortex.client.module.modules.TotemPopperModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Classic Minecraft-1.20.1 renderer path for Vortex entity overlays.
 *
 * Version 1.20.1 still uses a direct PoseStack/MultiBufferSource renderer.
 * Health, target, totem and custom-name information is intentionally combined
 * into one additional vanilla name-tag submission so that billboard handling,
 * text buffering and render ordering match the game renderer.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void vortex$renderEntityOverlay(LivingEntity entity, float entityYaw,
                                            float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, int packedLight,
                                            CallbackInfo ci) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || entity == client.player || !entity.isAlive()) return;

            StringBuilder text = new StringBuilder();
            int color = 0xFFFFFFFF;

            HealthIndicatorModule health = ModuleManager.INSTANCE.get(HealthIndicatorModule.class);
            if (health != null && health.isEnabled() && shouldShowHealth(health, entity)) {
                int hp = Math.max(0, Math.round(entity.getHealth()));
                String mode = health.mode.get();
                if ("Herzen".equals(mode)) {
                    int full = Math.min(10, hp / 2);
                    boolean half = (hp & 1) != 0;
                    for (int i = 0; i < Math.max(1, full); i++) text.append('\u2764');
                    if (half) text.append('\u2765');
                } else if ("Zahl+Herz".equals(mode)) {
                    text.append(hp).append(" \u2764");
                } else {
                    text.append(hp);
                }
                color = health.color.get();
            }

            TargetInfoModule target = ModuleManager.INSTANCE.get(TargetInfoModule.class);
            if (target != null && target.isEnabled() && entity instanceof Player) {
                double distance = client.player.distanceTo(entity);
                if (distance <= target.maxDistance.get()) {
                    if (target.range.get()) {
                        appendSeparator(text);
                        text.append(String.format(java.util.Locale.ROOT, "%.1fm", distance));
                        double reach = client.player.getAttributeValue(ForgeMod.ENTITY_REACH.get());
                        color = distance <= reach ? target.inRangeColor.get() : target.outRangeColor.get();
                    }
                    if (target.armor.get()) {
                        String gear = gearSummary(entity, target.durability.get());
                        if (!gear.isEmpty()) {
                            appendSeparator(text);
                            text.append(gear);
                        }
                    }
                }
            }

            TotemPopperModule pops = ModuleManager.INSTANCE.get(TotemPopperModule.class);
            if (pops != null && pops.isEnabled() && pops.overhead.get() && entity instanceof Player
                    && client.player.distanceTo(entity) <= pops.overheadRange.get()) {
                int amount = TotemPops.countFor(entity.getName().getString());
                if (amount > 0) {
                    appendSeparator(text);
                    text.append(amount).append(" totems");
                    color = pops.color.get();
                }
            }

            NametagModule tag = ModuleManager.INSTANCE.get(NametagModule.class);
            if (tag != null && tag.isEnabled() && entity instanceof Player
                    && client.player.distanceTo(entity) <= tag.range.get()) {
                appendSeparator(text);
                text.append(entity.getName().getString());
                if (tag.distance.get()) {
                    text.append(" ").append((int) client.player.distanceTo(entity)).append("m");
                }
                int alpha = (int) (255 * Math.max(0.2, Math.min(1.0, tag.opacity.get())));
                color = (alpha << 24) | (color & 0x00FFFFFF);
            }

            if (text.length() == 0) return;
            int nameTagColor = color & 0xFFFFFF;
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.25D, 0.0D);
            ((LivingEntityRendererNameTagInvoker) this).vortex$renderNameTag(
                    entity,
                    Component.literal(text.toString()).withStyle(style -> style.withColor(nameTagColor)),
                    poseStack, buffers, packedLight);
            poseStack.popPose();
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("LivingEntityOverlay", error);
        }
    }

    private static void appendSeparator(StringBuilder text) {
        if (text.length() > 0) text.append("  ");
    }

    private static String gearSummary(LivingEntity entity, boolean durability) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        StringBuilder result = new StringBuilder();
        for (EquipmentSlot slot : slots) {
            var stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            result.append(name.isEmpty() ? '?' : Character.toUpperCase(name.charAt(0)));
            if (durability && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                result.append((int) Math.round(100.0D * remaining / stack.getMaxDamage()));
            }
            result.append(' ');
        }
        return result.toString().trim();
    }

    private static boolean shouldShowHealth(HealthIndicatorModule mod, LivingEntity entity) {
        if (entity instanceof Player) return mod.showPlayers.get();
        if (entity instanceof Monster) return mod.showMonsters.get();
        if (entity instanceof Animal) return mod.showAnimals.get();
        return mod.showAnimals.get();
    }
}
