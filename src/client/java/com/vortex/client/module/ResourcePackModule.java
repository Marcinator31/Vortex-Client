package com.vortex.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Basisklasse fuer Module, die ein gebuendeltes Resource Pack an- und
 * ausschalten.
 *
 * APIs gegen die offizielle 1.21.11-Javadoc verifiziert:
 *  - Minecraft.getResourcePackManager() -> PackRepository
 *  - manager.getIds() -> Collection<String> (alle Pack-IDs)
 *  - manager.enable(String) / manager.disable(String) -> aktiviert/deaktiviert
 *  - Minecraft.reloadResources() -> wendet Aenderung an
 *
 * Der interne Pack-Name enthaelt die ID, die wir bei der Registrierung
 * vergeben haben. Da das genaue Praefix je nach Version variiert, suchen
 * wir die ID ueber "enthaelt unseren ID-Teil" statt exakter Gleichheit.
 */
public abstract class ResourcePackModule extends Module {

    private final String packIdPart;

    protected ResourcePackModule(String name, Category category, String packIdPart) {
        super(name, category);
        this.packIdPart = packIdPart;
    }

    @Override
    public void onEnable() {
        setPackEnabled(true);
    }

    @Override
    public void onDisable() {
        setPackEnabled(false);
    }

    private void setPackEnabled(boolean enable) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        PackRepository manager = client.getResourcePackRepository();
        if (manager == null) return;

        // Die vollstaendige Pack-ID finden, die unseren ID-Teil enthaelt.
        String fullId = null;
        for (String id : manager.getAvailableIds()) {
            if (id.contains(packIdPart)) {
                fullId = id;
                break;
            }
        }
        if (fullId == null) return; // Pack nicht gefunden -> nichts tun

        // Ist das Pack bereits im gewuenschten Zustand? Dann nichts tun --
        // wichtig, damit beim Client-Start (syncState) kein unnoetiger
        // reloadResources() ausgeloest wird, wenn sich gar nichts aendert.
        boolean alreadyEnabled = manager.getSelectedIds().contains(fullId);
        if (alreadyEnabled == enable) {
            return;
        }

        if (enable) {
            manager.addPack(fullId);
        } else {
            manager.removePack(fullId);
        }

        // Ressourcen neu laden, damit die Aenderung sofort wirkt.
        client.reloadResourcePacks();
    }
}
