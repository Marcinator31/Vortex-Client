package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.module.Module;

/**
 * Chat Highlight & Filter.
 *
 *  - hebt deinen Namen, deine Freunde und eigene Woerter farbig hervor
 *    (mit Ton, wenn dich jemand erwaehnt)
 *  - blendet Nachrichten mit Filterwoertern aus
 *  - fasst gleiche Nachrichten direkt hintereinander zu einer Zeile "(x3)"
 *    zusammen
 *
 * Woerter: /vchat highlight add|remove|list und /vchat filter add|remove|list.
 * Nur Anzeige -- es wird nichts gesendet.
 */
public class ChatFilterModule extends Module {

    public final BooleanSetting highlightName = new BooleanSetting("Highlight My Name", true);
    public final ColorSetting nameColor = new ColorSetting("Name Colour", 0xFFFFD23F).noGradient();
    public final BooleanSetting mentionSound = new BooleanSetting("Mention Sound", true);
    public final BooleanSetting highlightFriends = new BooleanSetting("Highlight Friends", true);
    public final BooleanSetting highlightWords = new BooleanSetting("Highlight Words", true);
    public final ColorSetting wordColor = new ColorSetting("Word Colour", 0xFFFF8A3D).noGradient();
    public final BooleanSetting filterWords = new BooleanSetting("Filter Words", true);
    public final BooleanSetting hideJoinLeave = new BooleanSetting("Hide Join/Leave", false);
    public final BooleanSetting stackDuplicates = new BooleanSetting("Stack Duplicates", true);

    public ChatFilterModule() {
        super("Chat Filter", Category.MISC);
        addSetting(highlightName);
        addSetting(nameColor);
        addSetting(mentionSound);
        addSetting(highlightFriends);
        addSetting(highlightWords);
        addSetting(wordColor);
        addSetting(filterWords);
        addSetting(hideJoinLeave);
        addSetting(stackDuplicates);
    }
}
