package com.vortex.legacy.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<Module>();
    private final Map<Module.Category, List<Module>> byCat = new EnumMap<Module.Category, List<Module>>(Module.Category.class);

    private ModuleManager() {}

    public void register(Module m) {
        modules.add(m);
        byCat.clear();
    }

    public List<Module> all() { return Collections.unmodifiableList(modules); }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> c) {
        for (Module m : modules) if (m.getClass() == c) return (T) m;
        return null;
    }

    public Module byName(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    public List<Module> byCategory(Module.Category c) {
        List<Module> l = byCat.get(c);
        if (l == null) {
            l = new ArrayList<Module>();
            for (Module m : modules) if (m.getCategory() == c) l.add(m);
            Collections.sort(l, new Comparator<Module>() {
                public int compare(Module a, Module b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            byCat.put(c, l);
        }
        return l;
    }

    public void tick() {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onTick(); } catch (Throwable t) { Errors.report(m.getName() + ".tick", t); }
        }
    }

    /** Modul-Tasten (aus Tastatur-Ereignissen, nur ohne offenes Menue). */
    public void onKey(int key) {
        if (key <= 0) return;
        for (Module m : modules) if (m.key.get() == key) m.toggle();
    }
}
