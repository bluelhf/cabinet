package blue.lhf.cabinette;

import blue.lhf.cabinette.commons.JarUtils;
import blue.lhf.cabinette.plugins.EmbeddedLoader;
import com.google.common.graph.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.*;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

@SuppressWarnings("UnstableApiUsage")
public final class PluginBootstrap {

    private record PluginEntry(PluginDescriptionFile description, Path path) {}

    private final EmbeddedLoader loader = new EmbeddedLoader(Bukkit.getServer());

    private final Map<String, PluginEntry> pluginEntries = new HashMap<>();
    private boolean acceptingPlugins = true;

    private static final Map<Path, PluginDescriptionFile> descriptions = new HashMap<>();

    private static PluginDescriptionFile getDescription(final Path path) {
        return descriptions.computeIfAbsent(path, PluginBootstrap::getDescription0);
    }

    private static PluginDescriptionFile getDescription0(final Path path) {
        try (final FileSystem jar = JarUtils.open(path)) {
            final Path descriptionPath = jar.getPath("plugin.yml");
            if (!Files.isRegularFile(descriptionPath)) {
                return null;
            }

            try (final InputStream stream = Files.newInputStream(descriptionPath)) {
                return new PluginDescriptionFile(stream);
            }

        } catch (final Exception e) {
            return null;
        }
    }

    boolean isPlugin(final Path path) {
        return getDescription(path) != null;
    }


    PluginBootstrap() {

    }

    public void registerPlugin(final Path path) {
        if (!acceptingPlugins) throw new IllegalStateException("Cannot register plugins after liftoff");
        final PluginDescriptionFile description;
        if (!isPlugin(path) || (description = getDescription(path)) == null)
            throw new IllegalArgumentException(path + " is not a valid plugin");

        this.pluginEntries.put(description.getName(), new PluginEntry(description, path));
    }

    public void liftoff() {
        this.acceptingPlugins = false;
        final Graph<PluginEntry> graph = constructDependencyGraph();
        final Iterable<PluginEntry> sorted = TopologicalSort.sort(graph);
        for (final PluginEntry entry : sorted) {
            load(entry.path);
        }
    }

    private void load(final Path path) {
        try {
            loader.enablePlugin(loader.loadPlugin(path.toFile()));
        } catch (final InvalidPluginException e) {
            e.printStackTrace();
        }
    }

    private Graph<PluginEntry> constructDependencyGraph() {
        final MutableGraph<PluginEntry> graph = GraphBuilder.directed().build();
        for (final PluginEntry path : this.pluginEntries.values()) {
            final List<String> dependencies = path.description.getDepend();

            for (final String dependencyName : dependencies) {
                final PluginEntry dependency = this.pluginEntries.get(dependencyName);
                if (dependency == null) continue;
                graph.putEdge(dependency, path);
            }
        }

        return graph;
    }
}
