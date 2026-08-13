package com.example.pvpclient.account;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet die gespeicherten Accounts und fuehrt den eigentlichen
 * "Switch" aus -- also das Umsetzen der aktiven Minecraft-Session.
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
     * Minecraft haelt die aktive Anmeldung in einem Session-Objekt.
     * "Account wechseln" heisst: ein neues Session-Objekt mit den Daten
     * des Ziel-Accounts bauen und in den Client setzen.
     *
     * ACHTUNG -- zwei reale Stolpersteine:
     *
     * 1) MinecraftClient.session ist final. Man kommt da nur per
     *    Reflection oder (sauberer) per Mixin/Accessor ran, der das
     *    Feld beschreibbar macht. Du brauchst also einen kleinen
     *    @Accessor-Mixin auf MinecraftClient, der das Sitzungsfeld freilegt.
     *    (Suchbegriff fuer dich: "fabric accessor mixin private field".)
     *
     * 2) Der accessToken im Account muss GUELTIG sein. Tokens laufen ab.
     *    Vor dem Switch ggf. per refreshToken erneuern (MicrosoftAuth).
     *    Mit einem abgelaufenen Token kannst du keine Online-Server
     *    joinen (Authentifizierung schlaegt fehl).
     *
     * Der genaue Konstruktor von Session aendert sich zwischen MC-Versionen.
     * Beim Build zeigt dir die IDE die erwarteten Parameter -- nimm die.
     */
    public void switchTo(Account account) {
        MinecraftClient client = MinecraftClient.getInstance();

        try {
            java.util.UUID uuid;
            String token;

            if (account.accessToken != null && !account.accessToken.isEmpty()) {
                // Echter Microsoft-Account: echten Token + echte UUID nutzen.
                token = account.accessToken;
                uuid = (account.uuid != null && !account.uuid.isEmpty())
                        ? java.util.UUID.fromString(account.uuid)
                        : net.minecraft.util.Uuids.getOfflinePlayerUuid(account.username);
            } else {
                // Offline-Fallback: deterministische UUID + Platzhalter-Token.
                uuid = net.minecraft.util.Uuids.getOfflinePlayerUuid(account.username);
                token = "0";
            }

            net.minecraft.client.session.Session session =
                new net.minecraft.client.session.Session(
                    account.username,
                    uuid,
                    token,
                    java.util.Optional.empty(),// xuid
                    java.util.Optional.empty() // clientId
                );

            ((com.example.pvpclient.mixin.client.MinecraftClientAccessor) client)
                .pvpclient$setSession(session);

            account.uuid = uuid.toString();

            System.out.println("[pvpclient] Account gewechselt zu: " + account.username);
        } catch (Throwable t) {
            System.out.println("[pvpclient] Account-Wechsel fehlgeschlagen: " + t);
        }
    }
}
