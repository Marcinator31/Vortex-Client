package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * Fullbright -- alles hell, keine Fackeln noetig.
 *
 * Die eigentliche Arbeit macht der GammaMixin: Solange dieses Modul an
 * ist, gibt er fuer die Gamma-Option einen hohen Wert zurueck und umgeht
 * damit die Vanilla-Begrenzung auf 1.0.
 *
 * Reines Visual-Feature, server-erlaubt.
 */
public class FullbrightModule extends Module {

    public FullbrightModule() {
        super("Fullbright", Category.MISC);
    }
}
