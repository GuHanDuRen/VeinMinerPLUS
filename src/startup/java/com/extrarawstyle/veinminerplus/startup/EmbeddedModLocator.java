package com.extrarawstyle.veinminerplus.startup;

import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

public final class EmbeddedModLocator implements IModFileCandidateLocator {
    private static final String EMBEDDED_MOD = "/META-INF/veinminerplus/veinminerplus-mod.jar";

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        if (!FMLLoader.isProduction()) {
            return;
        }
        var resource = EmbeddedModLocator.class.getResource(EMBEDDED_MOD);
        if (resource == null) {
            throw new IllegalStateException("Missing embedded VeinMinerPLUS mod: " + EMBEDDED_MOD);
        }

        try {
            var path = Path.of(resource.toURI());
            var nestedUri = new URI("jij:" + path.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
            // Keep the nested filesystem open; FML reads mod classes and resources from it later.
            var nestedFileSystem = FileSystems.newFileSystem(nestedUri, Map.of("packagePath", path));
            pipeline.addJarContent(
                    JarContents.of(nestedFileSystem.getPath("/")),
                    ModFileDiscoveryAttributes.DEFAULT,
                    IncompatibleFileReporting.WARN_ALWAYS);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to discover the embedded VeinMinerPLUS mod", e);
        }
    }
}
