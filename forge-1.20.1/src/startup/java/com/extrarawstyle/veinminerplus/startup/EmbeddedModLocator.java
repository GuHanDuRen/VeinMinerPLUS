package com.extrarawstyle.veinminerplus.startup;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

public final class EmbeddedModLocator extends AbstractJarFileModLocator {
    private static final String EMBEDDED_MOD = "/META-INF/veinminerplus/veinminerplus-mod.jar";

    @Override
    public String name() {
        return "veinminerplus_startup";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
        StartupBootstrap.install(String.valueOf(arguments.get("launchTarget")));
    }

    @Override
    public Stream<Path> scanCandidates() {
        if (!FMLLoader.isProduction()) {
            return Stream.empty();
        }
        var resource = EmbeddedModLocator.class.getResource(EMBEDDED_MOD);
        if (resource == null) {
            throw new IllegalStateException("Missing embedded VeinMinerPLUS mod: " + EMBEDDED_MOD);
        }
        try {
            Path path = Path.of(resource.toURI());
            URI nestedUri = new URI("jij:" + path.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
            // Keep the filesystem open; FML reads the embedded mod after discovery.
            var nestedFileSystem = FileSystems.newFileSystem(nestedUri, Map.of("packagePath", path));
            return Stream.of(nestedFileSystem.getPath("/"));
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to discover the embedded VeinMinerPLUS mod", exception);
        }
    }
}
