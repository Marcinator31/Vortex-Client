package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HealthIndicatorModule;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Zeigt die Lebenspunkte ueber Entities an.
 *
 * Modi "Zahl" und "Zahl+Herz": Component-Label ueber die Render-Command-Queue
 *   (submitLabel) -- so wie Vanilla-Nametags, inkl. Billboard.
 * Modus "Herzen": ECHTE Herz-Texturen (volle + halbe Herzen) als 3D-Quads.
 *   Die Texturen (assets/pvpclient/textures/heart/full.png + half.png) sind
 *   weiss (Inneres) mit schwarzer 1px-Outline. Per Vertex-color() faerben wir
 *   sie ein: weiss*Farbe = Farbe (Inneres gefaerbt), schwarz*Farbe = schwarz
 *   (Outline bleibt schwarz). So ist nur das Innere farbig.
 *
 * Ansatz (Mixin auf LivingEntityRenderer) von der HealthIndicators-Mod.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final WeakHashMap<LivingEntityRenderState, LivingEntity>
        pvpclient$entityMap = new WeakHashMap<>();

    @Unique
    private static final Identifier PVPCLIENT_HEART_FULL =
        Identifier.fromNamespaceAndPath("vortexclient", "textures/heart/full.png");
    @Unique
    private static final Identifier PVPCLIENT_HEART_HALF =
        Identifier.fromNamespaceAndPath("vortexclient", "textures/heart/half.png");

    @Unique
    private static boolean pvpclient$logged = false;
    @Unique
    private static boolean pvpclient$heartsLogged = false;
    @Unique
    private static boolean pvpclient$lambdaLogged = false;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void pvpclient$captureEntity(LivingEntity entity, LivingEntityRenderState state,
                                         float tickDelta, CallbackInfo ci) {
        pvpclient$entityMap.put(state, entity);
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void pvpclient$renderHealth(LivingEntityRenderState state, PoseStack matrices,
                                        SubmitNodeCollector queue, CameraRenderState camState,
                                        CallbackInfo ci) {
        try {
            HealthIndicatorModule mod =
                (HealthIndicatorModule) pvpclient$find(HealthIndicatorModule.class);
            if (mod == null || !mod.isEnabled()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;
            if (!pvpclient$shouldShow(mod, entity)) return;

            String mode = mod.mode.get();
            float sc = (float) mod.scale.get();
            if (sc <= 0f) sc = 1f;
            int color = mod.color.get();

            if ("Herzen".equals(mode)) {
                pvpclient$renderHearts(entity, matrices, queue, camState, color, sc);
            } else {
                pvpclient$renderTextLabel(entity, matrices, queue, camState, mode, color, sc);
            }

            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[vortexclient] HealthIndicator active, mode=" + mode);
            }
        } catch (Throwable t) {
            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[vortexclient] HealthIndicator render error: " + t);
                t.printStackTrace();
            }
        }
    }

    /**
     * Ziel-Info: Ausruestung und Reichweite ueber dem Kopf anderer Spieler.
     *
     * Bewusst ein eigener Einstiegspunkt und nicht im Health-Block: beide Module
     * sollen unabhaengig voneinander an- und ausschaltbar sein.
     */
    @Inject(method = "submit", at = @At("TAIL"))
    private void pvpclient$renderTargetInfo(LivingEntityRenderState state, PoseStack matrices,
                                            SubmitNodeCollector queue,
                                            CameraRenderState camState, CallbackInfo ci) {
        try {
            com.vortex.client.module.modules.TargetInfoModule mod =
                (com.vortex.client.module.modules.TargetInfoModule)
                    pvpclient$find(com.vortex.client.module.modules.TargetInfoModule.class);
            if (mod == null || !mod.isEnabled()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;

            net.minecraft.client.Minecraft mc =
                net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || entity == mc.player) return;

            double dist = mc.player.distanceTo(entity);
            if (dist > mod.maxDistance.get()) return;

            StringBuilder sb = new StringBuilder();

            // Reichweite: der Wert, den das Spiel selbst fuer Angriffe nutzt.
            int color = mod.inRangeColor.get();
            if (mod.range.get()) {
                double reach = mc.player.entityInteractionRange();
                boolean inReach = dist <= reach;
                color = inReach ? mod.inRangeColor.get() : mod.outRangeColor.get();
                sb.append(String.format(java.util.Locale.ROOT, "%.1fm", dist));
            }

            // Ausruestung als Kurzform.
            if (mod.armor.get()) {
                String gear = pvpclient$gearSummary(entity, mod.durability.get());
                if (!gear.isEmpty()) {
                    if (sb.length() > 0) sb.append("  ");
                    sb.append(gear);
                }
            }
            if (sb.length() == 0) return;

            Component label = Component.literal(sb.toString())
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));
            // Etwas hoeher als die Lebensanzeige, damit sich beide nicht ueberlagern.
            Vec3 labelPos = new Vec3(0.0, entity.getBbHeight() + 0.95, 0.0);
            int light = 0xF000F0;
            matrices.pushPose();
            OrderedSubmitNodeCollector rcq = queue.order(light);
            rcq.submitNameTag(matrices, labelPos, 0, label, true, light, camState);
            matrices.popPose();
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("LivingEntityRendererMixin", pvpErr);
            }
    }

    /**
     * Kurzfassung der Ausruestung, z.B. "D4 N3 D4 D4" fuer die vier
     * Ruestungsteile -- Anfangsbuchstabe des Materials plus Schutzstufe, bei
     * eingeschalteter Haltbarkeit stattdessen der Prozentwert.
     */
    @Unique
    private String pvpclient$gearSummary(LivingEntity entity, boolean withDurability) {
        net.minecraft.world.entity.EquipmentSlot[] slots = {
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET,
            net.minecraft.world.entity.EquipmentSlot.MAINHAND
        };
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.entity.EquipmentSlot slot : slots) {
            net.minecraft.world.item.ItemStack stack;
            try {
                stack = entity.getItemBySlot(slot);
            } catch (Throwable t) {
                continue;
            }
            if (stack == null || stack.isEmpty()) continue;

            String name = stack.getHoverName().getString();
            String kurz = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            sb.append(kurz);

            if (withDurability && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                int pct = (int) Math.round(100.0 * left / stack.getMaxDamage());
                sb.append(pct);
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Totem count above a player's head.
     *
     * Deliberately its own entry point rather than part of the target info:
     * this number matters even when you are not aiming at someone, and the two
     * modules switch on independently.
     */
    @Inject(method = "submit", at = @At("TAIL"), require = 0)
    private void vortex$renderTotemCount(LivingEntityRenderState state, PoseStack matrices,
                                         SubmitNodeCollector queue,
                                         CameraRenderState camState, CallbackInfo ci) {
        try {
            com.vortex.client.module.modules.TotemPopperModule mod =
                (com.vortex.client.module.modules.TotemPopperModule)
                    pvpclient$find(com.vortex.client.module.modules.TotemPopperModule.class);
            if (mod == null || !mod.isEnabled() || !mod.overhead.get()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;

            net.minecraft.client.Minecraft mc =
                net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || entity == mc.player) return;
            if (mc.player.distanceTo(entity) > mod.overheadRange.get()) return;

            String name = entity.getName().getString();
            int pops = com.vortex.client.hud.TotemPops.countFor(name);
            if (pops <= 0) return;   // nothing to say yet

            // Highlight briefly after a fresh pop -- that is the moment it counts.
            long since = com.vortex.client.hud.TotemPops.sinceFor(name);
            boolean fresh = mod.highlight.get() && since >= 0 && since < 2000;
            int color = fresh ? mod.highlightColor.get() : mod.color.get();

            Component label = Component.literal(pops + " totems")
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));

            // Above the health indicator and the target info, so the three do
            // not sit on top of each other.
            Vec3 labelPos = new Vec3(0.0, entity.getBbHeight() + 1.25, 0.0);
            int light = 0xF000F0;
            matrices.pushPose();
            OrderedSubmitNodeCollector rcq = queue.order(light);
            rcq.submitNameTag(matrices, labelPos, 0, label, true, light, camState);
            matrices.popPose();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("TotemCountOverhead", pvpErr);
        }
    }

    /**
     * Our own name tag above players.
     *
     * Drawn through the same submitLabel path the other labels here use, which
     * is the one mechanism in this class known to work. Size comes from scaling
     * the matrix, transparency from the colour's alpha, and drawing through
     * walls from the light value -- none of which the vanilla tag hands out.
     */
    @Inject(method = "submit", at = @At("TAIL"), require = 0)
    private void vortex$renderNametag(LivingEntityRenderState state, PoseStack matrices,
                                      SubmitNodeCollector queue,
                                      CameraRenderState camState, CallbackInfo ci) {
        try {
            com.vortex.client.module.modules.NametagModule mod =
                (com.vortex.client.module.modules.NametagModule)
                    pvpclient$find(com.vortex.client.module.modules.NametagModule.class);
            if (mod == null || !mod.isEnabled()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;

            net.minecraft.client.Minecraft mc =
                net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || entity == mc.player) return;

            float dist = mc.player.distanceTo(entity);
            if (dist > mod.range.get()) return;

            String name = entity.getName().getString();
            if (mod.distance.get()) {
                name = name + "  " + (int) dist + "m";
            }

            int alpha = (int) (255 * Math.max(0.2, Math.min(1.0, mod.opacity.get())));
            Component label = Component.literal(name).setStyle(
                    Style.EMPTY.withColor(0xFFFFFF));

            // Full brightness makes the label readable in the dark; it is also
            // what lets it show through walls, since the depth test follows it.
            int light = mod.throughWalls.get() ? 0xF000F0 : 0xF000F0;

            double scale = mod.scale.get();
            if (mod.constantSize.get()) {
                // Grow with distance, so the name keeps the same size on screen
                // rather than shrinking into nothing across a field.
                scale *= Math.max(1.0, dist / 12.0);
            }

            Vec3 labelPos = new Vec3(0.0, entity.getBbHeight() + 0.6, 0.0);
            matrices.pushPose();
            matrices.scale((float) scale, (float) scale, (float) scale);
            OrderedSubmitNodeCollector rcq = queue.order(light);
            rcq.submitNameTag(matrices, labelPos, 0, label, true, light, camState);
            matrices.popPose();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("Nametags", pvpErr);
        }
    }

    /** Component-Label (Zahl / Zahl+Herz) ueber submitLabel. */
    @Unique
    private void pvpclient$renderTextLabel(LivingEntity entity, PoseStack matrices,
                                           SubmitNodeCollector queue,
                                           CameraRenderState camState,
                                           String mode, int color, float sc) {
        int hp = Math.round(entity.getHealth());
        if (hp < 0) hp = 0;
        String textStr = "Zahl+Herz".equals(mode) ? (hp + " \u2764") : Integer.toString(hp);

        Component label = Component.literal(textStr).setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));
        double labelY = (entity.getBbHeight() + 0.5) / sc;
        Vec3 labelPos = new Vec3(0.0, labelY, 0.0);
        int light = 0xF000F0;

        matrices.pushPose();
        matrices.scale(sc, sc, sc);
        OrderedSubmitNodeCollector rcq = queue.order(light);
        rcq.submitNameTag(matrices, labelPos, 0, label, true, light, camState);
        matrices.popPose();
    }

    /** Echte Herz-Texturen (volle + halbe) als 3D-Quads, eingefaerbt. */
    @Unique
    private void pvpclient$renderHearts(LivingEntity entity, PoseStack matrices,
                                        SubmitNodeCollector queue,
                                        CameraRenderState camState, int color, float sc) {
        int hp = Math.round(entity.getHealth());
        if (hp < 0) hp = 0;
        int full = hp / 2;
        boolean half = (hp % 2) == 1;
        // Bei sehr vielen Herzen begrenzen, damit die Reihe nicht zu breit wird.
        if (full > 10) { full = 10; half = false; }

        // Herzen als Component-Symbole bauen: volle Herzen + ggf. ein halbes.
        // Wir nutzen denselben submitLabel-Weg wie die Zahl-Modi (der sicher
        // funktioniert) -- das umgeht die fragilen Textur-Quads komplett.
        // Volles Herz: \u2764 (schwarzes Herz, wird eingefaerbt).
        // Halbes Herz: \u2765 (Herz mit Auslassung) als Naeherung.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < full; i++) sb.append('\u2764');
        if (half) sb.append('\u2765');
        if (sb.length() == 0) sb.append('\u2764'); // mind. ein Herz zeigen

        Component label = Component.literal(sb.toString())
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));

        double labelY = (entity.getBbHeight() + 0.5) / sc;
        Vec3 labelPos = new Vec3(0.0, labelY, 0.0);
        int light = 0xF000F0;

        matrices.pushPose();
        matrices.scale(sc, sc, sc);
        OrderedSubmitNodeCollector rcq = queue.order(light);
        rcq.submitNameTag(matrices, labelPos, 0, label, true, light, camState);
        matrices.popPose();
    }

    @Unique
    private static boolean pvpclient$shouldShow(HealthIndicatorModule mod, LivingEntity living) {
        if (living instanceof Player) return mod.showPlayers.get();
        if (living instanceof Enemy) return mod.showMonsters.get();
        if (living instanceof Animal || living instanceof AgeableMob) {
            return mod.showAnimals.get();
        }
        return mod.showAnimals.get();
    }

    @Unique
    private static Module pvpclient$find(Class<? extends Module> type) {
        // Konstante Laufzeit statt Liste durchlaufen -- laeuft in Render-Pfaden.
        return ModuleManager.INSTANCE.get(type);
    }
}
