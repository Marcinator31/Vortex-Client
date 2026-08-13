package com.example.pvpclient.mixin.client;

import net.minecraft.client.render.fog.FogData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor-Mixin fuer FogData. FogData ist eine reine Datenklasse mit den
 * Nebel-Distanzen (Start/Ende fuer Umgebung und Render-Distanz). Wir brauchen
 * Setter, um die Nebel-Enden auf riesige Werte zu setzen -> kein Nebel mehr.
 *
 * Die Feldnamen ("environmentalEnd", "renderDistanceEnd", ...) stammen aus den
 * 1.21.11-Mappings. Falls der Build meckert, hier die Namen pruefen.
 */
@Mixin(FogData.class)
public interface FogDataAccessor {

    @Accessor("environmentalStart")
    void pvpclient$setEnvironmentalStart(float value);

    @Accessor("environmentalEnd")
    void pvpclient$setEnvironmentalEnd(float value);

    @Accessor("renderDistanceStart")
    void pvpclient$setRenderDistanceStart(float value);

    @Accessor("renderDistanceEnd")
    void pvpclient$setRenderDistanceEnd(float value);

    @Accessor("skyEnd")
    void pvpclient$setSkyEnd(float value);

    @Accessor("cloudEnd")
    void pvpclient$setCloudEnd(float value);
}
