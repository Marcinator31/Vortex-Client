package com.vortex.client.account;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Verwaltet die gespeicherten Accounts und fuehrt den eigentlichen
 * "Switch" aus -- also das Umsetzen der aktiven Minecraft-User.
 *
 * Das WIE des Login (Microsoft-OAuth) steckt in MicrosoftAuth.
 * Diese Klasse kuemmert sich nur darum, was man mit einem bereits
 * eingeloggten Account macht.
 */
public final class AccountManager {

    public static final AccountManager INSTANCE = new AccountManager();

    private final List<Account> accounts = new ArrayList<>();

    private AccountManager() {}

    public List<Account> getAccounts() {
        return accounts;
    }

    public void add(Account account) {
        accounts.add(account);
        // TODO: hier persistent speichern (siehe AccountStorage-Hinweis unten).
    }

    public void remove(Account account) {
        accounts.remove(account);
        // TODO: ebenfalls speichern.
    }

    /**
     * DER KERN DES SWITCHERS.
     *
     * Minecraft haelt die aktive Anmeldung in einem User-Objekt.
     * "Account wechseln" heisst: ein neues User-Objekt mit den Daten
     * des Ziel-Accounts bauen und in den Client setzen.
     *
     * ACHTUNG -- zwei reale Stolpersteine:
     *
     * 1) Minecraft.session ist final. Man kommt da nur per
     *    Reflection oder (sauberer) per Mixin/Accessor ran, der das
     *    Feld beschreibbar macht. Du brauchst also einen kleinen
     *    @Accessor-Mixin auf Minecraft, der das Sitzungsfeld freilegt.
     *    (Suchbegriff fuer dich: "fabric accessor mixin private field".)
     *
     * 2) Der accessToken im Account muss GUELTIG sein. Tokens laufen ab.
     *    Vor dem Switch ggf. per refreshToken erneuern (MicrosoftAuth).
     *    Mit einem abgelaufenen Token kannst du keine Online-Server
     *    joinen (Authentifizierung schlaegt fehl).
     *
     * Der genaue Konstruktor von User aendert sich zwischen MC-Versionen.
     * Beim Build zeigt dir die IDE die erwarteten Parameter -- nimm die.
     */
    public void switchTo(Account account) {
        Minecraft client = Minecraft.getInstance();

        try {
            java.util.UUID uuid;
            String token;

            if (account.accessToken != null && !account.accessToken.isEmpty()) {
                // Echter Microsoft-Account: echten Token + echte UUID nutzen.
                token = account.accessToken;
                uuid = (account.uuid != null && !account.uuid.isEmpty())
                        ? java.util.UUID.fromString(account.uuid)
                        : net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(account.username);
            } else {
                // Offline-Fallback: deterministische UUID + Platzhalter-Token.
                uuid = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(account.username);
                token = "0";
            }

            net.minecraft.client.User session =
                new net.minecraft.client.User(
                    account.username,
                    uuid,
                    token,
                    java.util.Optional.empty(),// xuid
                    java.util.Optional.empty() // clientId
                );

            ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                .pvpclient$setSession(session);

            account.uuid = uuid.toString();

            System.out.println("[vortexclient] Switched account to: " + account.username);
        } catch (Throwable t) {
            System.out.println("[vortexclient] Account switch failed: " + t);
        }
    }
}
