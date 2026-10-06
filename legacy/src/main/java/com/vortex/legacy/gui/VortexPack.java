package com.vortex.legacy.gui;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.resource.ResourceMetadataProvider;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.MetadataSerializer;

/**
 * Die Dateien aus unserer Jar (assets/...) als eingebautes Resource Pack.
 * 1.8.9 laedt Mod-Dateien sonst nicht (dafuer braeuchte es die Legacy
 * Fabric API) -- Logo, Shader und Co. waeren dann lila-schwarz.
 */
public class VortexPack implements ResourcePack {
    private static String pfad(Identifier id) { return "/assets/" + id.getNamespace() + "/" + id.getPath(); }

    @Override
    public InputStream open(Identifier id) throws IOException {
        InputStream in = VortexPack.class.getResourceAsStream(pfad(id));
        if (in == null) throw new FileNotFoundException(id.toString());
        return in;
    }

    @Override public boolean contains(Identifier id) { return VortexPack.class.getResource(pfad(id)) != null; }

    @Override
    public Set<String> getNamespaces() {
        Set<String> s = new HashSet<String>();
        s.add("vortexclient");
        s.add("minecraft");
        return s;
    }

    @Override public <T extends ResourceMetadataProvider> T parseMetadata(MetadataSerializer serializer, String key) { return null; }
    @Override public BufferedImage getIcon() { return null; }
    @Override public String getName() { return "Vortex Client"; }
}
