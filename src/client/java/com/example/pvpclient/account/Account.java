package com.example.pvpclient.account;

/**
 * Ein einzelner gespeicherter Account.
 *
 * WICHTIG zu den Feldern:
 *  - username / uuid  -> harmlos, nur Anzeige
 *  - accessToken      -> das ist ein GEHEIMNIS. Wer den hat, ist eingeloggt.
 *                        Niemals committen, niemals loggen, niemals teilen.
 *  - refreshToken     -> ebenfalls geheim. Damit holt man neue accessTokens.
 *
 * Deshalb wird die gespeicherte Account-Datei (siehe AccountManager)
 * NICHT ins Git-Repo gehoeren. Sie liegt lokal im .minecraft-Ordner.
 */
public class Account {

    public String username;
    public String uuid;

    // Diese beiden sind sensibel -- siehe Klassen-Kommentar.
    public transient String accessToken;
    public transient String refreshToken;

    public Account() {
        // Leerer Konstruktor fuer JSON-Deserialisierung.
    }

    public Account(String username, String uuid) {
        this.username = username;
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return username == null ? "(leer)" : username;
    }
}
