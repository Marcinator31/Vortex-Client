package com.example.pvpclient.core.setting;

/**
 * Eine Auswahl aus mehreren festen Optionen (z.B. Haltbarkeits-Anzeige:
 * "Prozent" / "Schlaege" / "Balken"). Im ClickGUI klickt man die Zeile an,
 * um zur naechsten Option durchzuschalten.
 *
 * Gespeichert wird der Options-TEXT (nicht der Index), damit gespeicherte
 * Configs auch dann noch stimmen, wenn spaeter die Reihenfolge der Optionen
 * geaendert wird.
 */
public class ModeSetting extends Setting {

    private final String[] options;
    private int index;

    public ModeSetting(String name, int defaultIndex, String... options) {
        super(name);
        this.options = options;
        this.index = clampIndex(defaultIndex);
    }

    /** Die aktuell gewaehlte Option als Text. */
    public String get() {
        return options[index];
    }

    /** Index der aktuellen Option (0-basiert). */
    public int getIndex() {
        return index;
    }

    /** Prueft, ob die aktuelle Option dem gegebenen Text entspricht. */
    public boolean is(String option) {
        return options[index].equals(option);
    }

    /** Zur naechsten Option weiterschalten (am Ende wieder von vorn). */
    public void cycle() {
        index = (index + 1) % options.length;
    }

    /** Setzt die Option direkt per Text (falls vorhanden). */
    public void set(String option) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(option)) {
                index = i;
                return;
            }
        }
    }

    private int clampIndex(int i) {
        if (i < 0) return 0;
        if (i >= options.length) return options.length - 1;
        return i;
    }

    @Override
    public String serialize() {
        return options[index];
    }

    @Override
    public void deserialize(String value) {
        // Gespeicherten Options-Text suchen; falls nicht gefunden, Default lassen.
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                index = i;
                return;
            }
        }
    }
}
