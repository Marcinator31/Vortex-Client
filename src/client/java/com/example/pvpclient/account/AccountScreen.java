package com.example.pvpclient.account;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Der Account-Switcher-Bildschirm mit echtem Microsoft-Login.
 *
 * "Microsoft-Login" startet den Device Code Flow: der Client holt einen Code,
 * zeigt ihn an und oeffnet die Login-Seite im Browser. Der Nutzer gibt den Code
 * ein und loggt sich bei Microsoft ein; sobald das fertig ist, erscheint der
 * Account in der Liste. Der Login laeuft in einem eigenen Thread, damit das
 * Spiel nicht einfriert.
 *
 * Darunter pro gespeichertem Account "Wechseln" und "Entfernen".
 */
public class AccountScreen extends Screen {

    // Status-Text fuer den laufenden Login (Code, Fehler, Erfolg).
    private volatile String statusLine = "";
    private volatile String codeLine = "";
    private volatile boolean loggingIn = false;

    // Eingabefeld fuer den aus dem Browser kopierten Code (klassischer Weg).
    private net.minecraft.client.gui.widget.TextFieldWidget codeField;

    public AccountScreen() {
        super(Text.literal("Accounts"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - 100;
        int y = 44;

        // "Microsoft-Login" -- startet den Device Code Flow.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(loggingIn ? "Login laeuft..." : "+ Microsoft-Login"),
            btn -> startMicrosoftLogin()
        ).dimensions(x, y, 200, 20).build());

        y += 24;

        // --- Klassischer Browser-Login (ohne eigene Azure-App) ---
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("1. Login-Seite oeffnen"),
            btn -> openLoginPage()
        ).dimensions(x, y, 200, 20).build());

        y += 24;

        // Feld zum Einfuegen der Adresse aus dem Browser.
        this.codeField = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, x, y, 200, 20,
                Text.literal("Adresse einfuegen"));
        this.codeField.setMaxLength(2000);
        this.codeField.setPlaceholder(Text.literal("Adresse mit code=... einfuegen"));
        this.addDrawableChild(this.codeField);

        y += 24;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("2. Code einloesen"),
            btn -> redeemCode()
        ).dimensions(x, y, 200, 20).build());

        y += 30;

        // Pro gespeichertem Account: "Wechseln" + "Entfernen".
        for (Account acc : new java.util.ArrayList<>(AccountManager.INSTANCE.getAccounts())) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Wechseln: " + acc.username),
                btn -> {
                    AccountManager.INSTANCE.switchTo(acc);
                    this.clearAndInit();
                }
            ).dimensions(x, y, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("X"),
                btn -> {
                    AccountManager.INSTANCE.remove(acc);
                    this.clearAndInit();
                }
            ).dimensions(x + 155, y, 45, 20).build());

            y += 24;
        }

        // Schliessen-Button.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Schliessen"),
            btn -> this.close()
        ).dimensions(x, this.height - 30, 200, 20).build());
    }

    /** Startet den Microsoft Device Code Flow in einem Hintergrund-Thread. */
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("pvpclient-auth");

    /**
     * Macht aus einer Exception eine lesbare Zeile. Wichtig: getMessage() ist oft
     * null (z.B. bei NullPointerException) -- dann stand frueher nur "Fehler: null"
     * da. Hier kommt zusaetzlich immer der Fehlertyp mit, und die Ursache-Kette.
     */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 3) {
            if (depth > 0) sb.append(" <- ");
            String msg = cur.getMessage();
            sb.append(cur.getClass().getSimpleName());
            if (msg != null && !msg.isEmpty()) sb.append(": ").append(msg);
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    /** Schritt 1: Microsoft-Login-Seite im Browser oeffnen. */
    private void openLoginPage() {
        String url = MicrosoftAuth.getAuthUrl();
        statusLine = "Im Browser einloggen, dann die Adresse kopieren.";
        codeLine = "Sie enthaelt 'code=' und sieht leer aus.";
        try {
            Util.getOperatingSystem().open(new java.net.URI(url));
        } catch (Throwable t) {
            LOGGER.error("[pvpclient] Browser konnte nicht geoeffnet werden", t);
            statusLine = "Browser ging nicht auf - Adresse steht in latest.log";
            LOGGER.info("[pvpclient] Login-Adresse: {}", url);
        }
    }

    /** Schritt 2: eingefuegte Adresse einloesen und Account anlegen. */
    private void redeemCode() {
        if (loggingIn) return;
        String pasted = (codeField != null) ? codeField.getText() : "";
        if (pasted == null || pasted.trim().isEmpty()) {
            statusLine = "Bitte zuerst die Adresse aus dem Browser einfuegen.";
            return;
        }
        loggingIn = true;
        statusLine = "Loese Code ein...";
        codeLine = "";
        this.clearAndInit();

        Thread t = new Thread(() -> {
            try {
                Account acc = MicrosoftAuth.loginWithCode(pasted);
                AccountManager.INSTANCE.add(acc);
                statusLine = "Eingeloggt als " + acc.username + "!";
                codeLine = "";
            } catch (Throwable e) {
                LOGGER.error("[pvpclient] Code-Einloesung fehlgeschlagen", e);
                String msg = describe(e);
                statusLine = "Fehler: " + msg;
                // Bei genau diesem Microsoft-Fehler direkt sagen, was zu tun ist --
                // er bedeutet immer dasselbe: die Client-ID ist nicht registriert.
                if (msg.contains("unauthorized_client") || msg.contains("AADSTS700016")) {
                    statusLine = "Client-ID nicht registriert (AADSTS700016)";
                    codeLine = "Eigene Azure-ID in config/pvpclient-clientid.txt eintragen";
                } else {
                    codeLine = "Details siehe latest.log";
                }
            } finally {
                loggingIn = false;
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                    if (this.client != null && this.client.currentScreen == this) {
                        this.clearAndInit();
                    }
                });
            }
        }, "pvpclient-ms-code");
        t.setDaemon(true);
        t.start();
    }

    private void startMicrosoftLogin() {
        if (loggingIn) return;
        loggingIn = true;
        statusLine = "Verbinde mit Microsoft...";
        codeLine = "";
        this.clearAndInit();

        Thread t = new Thread(() -> {
            try {
                Account acc = MicrosoftAuth.login(code -> {
                    // Code anzeigen + Browser oeffnen.
                    codeLine = "Code: " + code.userCode;
                    statusLine = "Oeffne Browser... Code eingeben: " + code.userCode;
                    try {
                        Util.getOperatingSystem().open(new java.net.URI(code.verificationUri));
                    } catch (Throwable ignored) {
                        statusLine = "Gehe zu " + code.verificationUri
                                + " und gib den Code ein.";
                    }
                });

                // Erfolg -> Account speichern.
                AccountManager.INSTANCE.add(acc);
                statusLine = "Eingeloggt als " + acc.username + "!";
                codeLine = "";
            } catch (Throwable e) {
                // Vollstaendigen Fehler ins Spiel-Log schreiben (latest.log) --
                // die GUI-Zeile ist kurz, im Log steht die ganze Ursache.
                LOGGER.error("[pvpclient] Microsoft-Login fehlgeschlagen", e);
                statusLine = "Fehler: " + describe(e);
                codeLine = "Details siehe latest.log";
            } finally {
                loggingIn = false;
                // GUI im Main-Thread neu aufbauen.
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                    if (this.client != null && this.client.currentScreen == this) {
                        this.clearAndInit();
                    }
                });
            }
        }, "pvpclient-ms-login");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Aktueller Account-Name oben.
        String current = "Aktuell: " + this.client.getSession().getUsername();
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(current),
                this.width / 2, 18, 0xFFFFFF00);

        // Status + Code (waehrend/nach dem Login).
        if (!statusLine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusLine),
                    this.width / 2, this.height - 52, 0xFF55FF55);
        }
        if (!codeLine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(codeLine),
                    this.width / 2, this.height - 64, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
