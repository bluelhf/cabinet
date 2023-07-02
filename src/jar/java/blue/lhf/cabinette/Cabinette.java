package blue.lhf.cabinette;

import blue.lhf.cabinet.commons.JarUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.*;

import static java.nio.file.FileVisitResult.CONTINUE;

public class Cabinette extends JavaPlugin {

    public static final Logger LOGGER = Logger.getLogger(Cabinette.class.getName());

    @Override
    public void onEnable() {
        final Path targetDir = getDataFolder().toPath();

        final PluginBootstrap bootstrap = new PluginBootstrap();

        try (final FileSystem self = JarUtils.self()) {
            final Path root = self.getPath("files");
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                private Path target(final Path path) {
                    return targetDir.resolve(root.relativize(path).toString());
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                    LOGGER.log(Level.SEVERE, exc, () -> "Failed to read file " + file);
                    return CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target(dir));
                    return CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path target = target(file);
                    if (bootstrap.isPlugin(target))
                        bootstrap.registerPlugin(target);

                    if (Files.exists(target)) return CONTINUE;
                    Files.copy(file, target);
                    return CONTINUE;
                }
            });

            bootstrap.liftoff();
        } catch (final IOException | URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Failed to extract files", e);
        }
    }
}
