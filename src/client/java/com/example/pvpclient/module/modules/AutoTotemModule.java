package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * Auto Totem: legt automatisch ein Totem of Undying in die Off-Hand, sobald der
 * Off-Hand-Slot leer ist und ein Totem im Inventar liegt.
 *
 * Auf PvP-Servern ist ein Totem in der Off-Hand ueberlebenswichtig -- bricht es,
 * sorgt dieses Modul dafuer, dass sofort das naechste nachrueckt.
 *
 * Hinweis: Das Modul bewegt Items ueber Slot-Klicks (wie ein Spieler im
 * Inventar) und sendet damit Pakete an den Server. Sehr schnelle/haeufige
 * Totem-Wechsel koennen auf strengen Anticheat-Servern auffallen -- deshalb ist
 * ein kleiner Cooldown eingebaut.
 */
public class AutoTotemModule extends Module {

    public AutoTotemModule() {
        super("Auto Totem", Category.PVP);
    }
}
