package blue.lhf.cabinette.plugins;

import com.google.common.base.Preconditions;
import org.bukkit.*;
import org.bukkit.configuration.serialization.*;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.*;
import java.util.regex.Pattern;

import static java.util.logging.Level.SEVERE;

@SuppressWarnings({"removal", "deprecation"})
public class EmbeddedLoader implements PluginLoader {
    private static final Logger LOGGER = Logger.getLogger("Cabinette Embedded Loader");
    final Server server;
    private final Pattern[] fileFilters = new Pattern[]{Pattern.compile("\\.jar$")};
    private final List<EmbeddedClassLoader> loaders = new CopyOnWriteArrayList<>();
    private final EmbeddedLibraryLoader libraryLoader;

    public EmbeddedLoader(final Server instance) {
        Preconditions.checkArgument(instance != null, "Server cannot be null");
        server = instance;

        EmbeddedLibraryLoader libraryLoader = null;
        try {
            libraryLoader = new EmbeddedLibraryLoader(server.getLogger());
        } catch (final NoClassDefFoundError ex) {
            // Provided depends were not added back
            server.getLogger().warning("Could not initialize EmbeddedLibraryLoader (missing dependencies?)");
        }
        this.libraryLoader = libraryLoader;
    }

    public @NotNull Plugin loadPlugin(final Path path) throws InvalidDescriptionException, IOException, InvalidPluginException {
        final PluginDescriptionFile description = getPluginDescription(path);

        final Path dataDir = path.getParent().resolve(description.getName());
        if (Files.exists(dataDir) && !Files.isDirectory(dataDir)) {
            throw new FileAlreadyExistsException(path.getFileName() + " can't be loaded because its data directory at " + Path.of(".").relativize(dataDir) + " is occupied by a regular file");
        }

        server.getUnsafe().checkSupported(description);

        final EmbeddedClassLoader loader;
        try {

            loader = new EmbeddedClassLoader(
                this, Bukkit.class.getClassLoader(), description, dataDir, path,
                (libraryLoader != null) ? libraryLoader.createLoader(description) : null
            );

        } catch (final Throwable ex) {
            if (ex instanceof final InvalidPluginException ipex) throw ipex;
            throw new InvalidPluginException(ex);
        }

        loaders.add(loader);
        return loader.plugin;
    }

    private PluginDescriptionFile getPluginDescription(final Path path) throws InvalidDescriptionException, IOException {
        try (final FileSystem jar = FileSystems.newFileSystem(path, Map.of())) {
            return new PluginDescriptionFile(Files.newInputStream(jar.getPath("plugin.yml")));
        }
    }

    @Override
    public @NotNull Plugin loadPlugin(final @NotNull File file) throws InvalidPluginException {
        try {
            return loadPlugin(file.toPath());
        } catch (final Throwable t) {
            if (t instanceof final InvalidPluginException ipex) throw ipex;
            throw new InvalidPluginException(t);
        }
    }

    @Override
    public @NotNull PluginDescriptionFile getPluginDescription(@NotNull final File file) throws InvalidDescriptionException {
        try {
            return getPluginDescription(file.toPath());
        } catch (final IOException e) {
            throw new InvalidDescriptionException(e);
        }
    }

    @Override
    public Pattern[] getPluginFileFilters() {
        return fileFilters.clone();
    }

    @Override
    public @NotNull Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(final Listener listener, final @NotNull Plugin plugin) {
        Preconditions.checkArgument(listener != null, "Listener can not be null");

        final Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();
        final Set<Method> methods;
        try {
            final Method[] publicMethods = listener.getClass().getMethods();
            final Method[] privateMethods = listener.getClass().getDeclaredMethods();
            methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
            Collections.addAll(methods, publicMethods);
            Collections.addAll(methods, privateMethods);
        } catch (final NoClassDefFoundError e) {
            plugin.getLogger().severe("Plugin " + plugin.getDescription().getFullName() + " has failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return ret;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            // Do not register bridge or synthetic methods to avoid event duplication
            // Fixes SPIGOT-893
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().log(SEVERE, () -> plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            final Set<RegisteredListener> eventSet = ret.computeIfAbsent(eventClass, k -> new HashSet<>());

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    final Warning warning = clazz.getAnnotation(Warning.class);
                    final Warning.WarningState warningState = server.getWarningState();
                    if (!warningState.printFor(warning)) {
                        break;
                    }
                    plugin.getLogger().log(
                        Level.WARNING,
                        String.format(
                            "\"%s\" has registered a listener for %s on method \"%s\", but the event is Deprecated. \"%s\"; please notify the authors %s.",
                            plugin.getDescription().getFullName(),
                            clazz.getName(),
                            method.toGenericString(),
                            (warning != null && warning.reason().length() != 0) ? warning.reason() : "Server performance will be affected",
                            Arrays.toString(plugin.getDescription().getAuthors().toArray())),
                        warningState == Warning.WarningState.ON ? new AuthorNagException(null) : null);
                    break;
                }
            }

            final EventExecutor executor = (eventListener, event) -> {
                try {
                    if (!eventClass.isAssignableFrom(event.getClass())) {
                        return;
                    }

                    method.invoke(eventListener, event);

                } catch (final InvocationTargetException ex) {
                    throw new EventException(ex.getCause());
                } catch (final Throwable t) {
                    throw new EventException(t);
                }
            };
            eventSet.add(new RegisteredListener(listener, executor, eh.priority(), plugin, eh.ignoreCancelled()));
        }
        return ret;
    }

    @Override
    public void enablePlugin(final Plugin plugin) {
        Preconditions.checkArgument(plugin instanceof JavaPlugin, "Plugin is not associated with this PluginLoader");

        if (!plugin.isEnabled()) {
            plugin.getLogger().info("Enabling " + plugin.getDescription().getFullName());

            final JavaPlugin jPlugin = (JavaPlugin) plugin;
            final EmbeddedClassLoader pluginLoader = (EmbeddedClassLoader) jPlugin.getClass().getClassLoader();

            if (!loaders.contains(pluginLoader)) {
                loaders.add(pluginLoader);
                LOGGER.log(Level.WARNING, () -> "Enabled plugin with unregistered EmbeddedClassLoader " + plugin.getDescription().getFullName());
            }

            try {
                final Method setEnabled = JavaPlugin.class.getDeclaredMethod("setEnabled", boolean.class);
                setEnabled.setAccessible(true);
                setEnabled.invoke(plugin, true);
            } catch (final Throwable ex) {
                LOGGER.log(SEVERE, ex, () -> "Error occurred while enabling " + plugin.getDescription().getFullName() + " (Is it up to date?)");
            }

            // Perhaps abort here, rather than continue going, but as it stands,
            // an abort is not possible the way it's currently written
            server.getPluginManager().callEvent(new PluginEnableEvent(plugin));
        }
    }

    @Override
    public void disablePlugin(final Plugin plugin) {
        Preconditions.checkArgument(plugin instanceof JavaPlugin, "Plugin is not associated with this PluginLoader");

        if (plugin.isEnabled()) {
            final String message = String.format("Disabling %s", plugin.getDescription().getFullName());
            plugin.getLogger().info(message);

            server.getPluginManager().callEvent(new PluginDisableEvent(plugin));

            final JavaPlugin jPlugin = (JavaPlugin) plugin;
            final ClassLoader cloader = jPlugin.getClass().getClassLoader();

            try {
                final Method setEnabled = JavaPlugin.class.getDeclaredMethod("setEnabled", boolean.class);
                setEnabled.setAccessible(true);
                setEnabled.invoke(plugin, false);
            } catch (final Throwable ex) {
                LOGGER.log(SEVERE, ex, () -> "Error occurred while disabling " + plugin.getDescription().getFullName() + " (Is it up to date?)");
            }

            if (cloader instanceof final EmbeddedClassLoader loader) {
                loaders.remove(loader);

                final Collection<Class<?>> classes = loader.getClasses();

                for (final Class<?> clazz : classes) {
                    removeClass(clazz);
                }

                try {
                    loader.close();
                } catch (final IOException ignored) {

                }
            }
        }
    }

    Class<?> getClassByName(final String name, final boolean resolve, final PluginDescriptionFile description) {
        for (final EmbeddedClassLoader loader : loaders) {
            try {
                return loader.loadClass0(name, resolve, false, ((SimplePluginManager) server.getPluginManager()).isTransitiveDepend(description, loader.plugin.getDescription()));
            } catch (final ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    void setClass(final String ignoredName, final Class<?> clazz) {
        if (ConfigurationSerializable.class.isAssignableFrom(clazz)) {
            final Class<? extends ConfigurationSerializable> serializable = clazz.asSubclass(ConfigurationSerializable.class);
            ConfigurationSerialization.registerClass(serializable);
        }
    }

    private void removeClass(final Class<?> clazz) {
        if (ConfigurationSerializable.class.isAssignableFrom(clazz)) {
            final Class<? extends ConfigurationSerializable> serializable = clazz.asSubclass(ConfigurationSerializable.class);
            ConfigurationSerialization.unregisterClass(serializable);
        }
    }
}
