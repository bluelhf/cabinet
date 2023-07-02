package blue.lhf.cabinet;

import blue.lhf.cabinette.commons.JarUtils;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static blue.lhf.cabinet.utils.Errors.reportError;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@SuppressWarnings({"deprecation", "unused"})
public final class Cabinet extends JavaPlugin {

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command,
                             final @NotNull String label, final String[] args) {
        final ArrayDeque<String> argStack = new ArrayDeque<>(List.of(args));
        if (argStack.isEmpty()) {
            return false;
        }

        final String cabinetName = argStack.pop();
        final Path pluginsDir = getDataFolder().toPath().getParent();
        final Path cabinetPath = pluginsDir.resolve(cabinetName + ".jar");
        if (!createCabinet(sender, argStack, cabinetName, cabinetPath)) {
            try {
                Files.deleteIfExists(cabinetPath);
            } catch (final IOException e) {
                reportError(sender, e, "trying to delete the failed cabinet.");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command command, final @NotNull String alias, final String[] args) {
        if (args.length <= 1) return Collections.emptyList();
        final String prefix = args[args.length - 1];
        final Path root = getDataFolder().toPath().getParent();
        try (final Stream<Path> completions = Files.walk(root, prefix.split("/", -1).length)) {

            return completions.map(root::relativize)
                       .map(path -> path + (Files.isDirectory(root.resolve(path)) ? "/" : ""))
                       .filter(name -> name.startsWith(prefix)).toList();

        } catch (final IOException e) {
            return Collections.emptyList();
        }
    }

    private boolean createCabinet(@NotNull final CommandSender sender, final Collection<String> files,
                                  final String name, final Path cabinetPath) {

        final Path pluginsDir = getDataFolder().toPath().getParent();

        try (final JarBuilder builder = new JarBuilder(Files.newOutputStream(cabinetPath))) {
            builder.add("plugin.yml", stream -> {
                final YamlConfiguration pluginYml = new YamlConfiguration();

                pluginYml.set("name", name);
                pluginYml.set("main", "blue.lhf.cabinette.Extractor");
                pluginYml.set("version", getDescription().getVersion());
                pluginYml.set("api-version", "1.13");
                pluginYml.set("description", "A self-extracting meta-plugin");
                pluginYml.set("load", "POSTWORLD");

                pluginYml.set("author", sender.getName());
                pluginYml.set("website", "https://github.com/bluelhf/cabinet");

                stream.write(pluginYml.saveToString().getBytes(UTF_8));
            });

            try (final FileSystem self = JarUtils.self()) {
                builder.add(self.getPath("inject"));
            } catch (final URISyntaxException e) {
                reportError(sender, e, "getting Cabinet's JAR URI.");
                return false;
            } catch (final IOException e) {
                reportError(sender, e, "trying to move files between Cabinet and the Cabinette.");
                return false;
            }

            for (final String arg : files) {
                try {
                    builder.add(pluginsDir, pluginsDir.resolve(arg), "files");
                } catch (final IOException e) {
                    reportError(sender, e, "trying to add " + arg + " to the cabinette.");
                    return false;
                }
            }
        } catch (final IOException e) {
            reportError(sender, e, "creating the cabinet.");
            return false;
        }

        sender.sendMessage(text("Cabinette created successfully!"));
        sender.sendMessage(empty()
                               .append(text("You can find it at ", GREEN))
                               .append(text(pluginsDir.relativize(cabinetPath).toString(), WHITE))
                               .append(text(".", GREEN)));

        return true;
    }

}
