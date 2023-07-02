package blue.lhf.cabinette.plugins;

import com.destroystokyo.paper.utils.PaperPluginLogger;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.*;
import java.util.logging.*;

@SuppressWarnings({"UnstableApiUsage", "removal"})
public class EmbeddedClassLoader extends URLClassLoader implements ConfiguredPluginClassLoader {
    private final EmbeddedLoader loader;
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    private final PluginDescriptionFile description;
    private final Path dataFolder;
    private final FileSystem jar;
    private final Manifest manifest;
    private final URL url;
    private final ClassLoader libraryLoader;
    final JavaPlugin plugin;
    private JavaPlugin pluginInit;
    private IllegalStateException pluginState;
    private final Logger logger;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    EmbeddedClassLoader(final EmbeddedLoader loader, final ClassLoader parent, final PluginDescriptionFile description, final Path dataPath, final Path jarPath, final ClassLoader libraryLoader) throws IOException, InvalidPluginException {
        super(new URL[] {jarPath.toUri().toURL()}, parent);
        Preconditions.checkArgument(loader != null, "Loader cannot be null");

        this.loader = loader;
        this.description = description;
        this.dataFolder = dataPath;
        try {
            this.jar = FileSystems.newFileSystem(new URI("jar:" + jarPath), Map.of());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid JAR path: " + jarPath, e);
        }
        try (final InputStream manifestInputStream = Files.newInputStream(this.jar.getPath("META-INF/MANIFEST.MF"))) {
            this.manifest = new Manifest(manifestInputStream);
        }
        this.url = jarPath.toUri().toURL();
        this.libraryLoader = libraryLoader;

        this.logger = PaperPluginLogger.getLogger(description);

        try {
            final Class<?> jarClass;
            try {
                jarClass = Class.forName(description.getMain(), true, this);
            } catch (final ClassNotFoundException ex) {
                throw new InvalidPluginException("Cannot find main class `" + description.getMain() + "'", ex);
            }

            final Class<? extends JavaPlugin> pluginClass;
            try {
                pluginClass = jarClass.asSubclass(JavaPlugin.class);
            } catch (final ClassCastException ex) {
                throw new InvalidPluginException("main class `" + description.getMain() + "' does not extend JavaPlugin", ex);
            }

            plugin = pluginClass.getConstructor().newInstance();
        } catch (final IllegalAccessException | NoSuchMethodException ex) {
            throw new InvalidPluginException("No public constructor", ex);
        } catch (final InstantiationException ex) {
            throw new InvalidPluginException("Abnormal plugin type", ex);
        } catch (final InvocationTargetException e) {
            throw new InvalidPluginException("Plugin constructor threw an exception");
        }
    }

    @Override
    public URL getResource(final String name) {
        return findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return findResources(name);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        return loadClass0(name, resolve, true, true);
    }

    Class<?> loadClass0(final String name, final boolean resolve, final boolean checkGlobal, final boolean checkLibraries) throws ClassNotFoundException {
        try {
            final Class<?> result = super.loadClass(name, resolve);

            if (checkGlobal || result.getClassLoader() == this) {
                return result;
            }
        } catch (final ClassNotFoundException ignored) {
        }

        if (checkLibraries && libraryLoader != null) {
            try {
                return libraryLoader.loadClass(name);
            } catch (final ClassNotFoundException ignored) {
            }
        }

        if (checkGlobal) return loader.getClassByName(name, resolve, description);
        throw new ClassNotFoundException(name);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        if (name.startsWith("org.bukkit.") || name.startsWith("net.minecraft.")) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result = classes.get(name);
        if (result != null) return result;

        final String path = name.replace('.', '/').concat(".class");
        final Path entry = jar.getPath(path);

        if (Files.exists(entry)) {
            byte[] classBytes;

            try {
                classBytes = Files.readAllBytes(entry);
            } catch (final IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }

            classBytes = loader.server.getUnsafe().processClass(description, path, classBytes);

            final int dot = name.lastIndexOf('.');
            if (dot != -1) {
                final String pkgName = name.substring(0, dot);
                if (getDefinedPackage(pkgName) == null) {
                    try {
                        if (manifest != null) {
                            definePackage(pkgName, manifest, url);
                        } else {
                            definePackage(pkgName, null, null, null, null, null, null, null);
                        }
                    } catch (final IllegalArgumentException ex) {
                        if (getDefinedPackage(pkgName) == null) {
                            throw new IllegalStateException("Cannot find package " + pkgName);
                        }
                    }
                }
            }

            result = defineClass(name, classBytes, 0, classBytes.length, (CodeSource) null);
        }

        if (result == null) result = super.findClass(name);
        loader.setClass(name, result);
        classes.put(name, result);

        return result;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            jar.close();
        }
    }

    Collection<Class<?>> getClasses() {
        return classes.values();
    }

    @Override
    public PluginMeta getConfiguration() {
        return description;
    }

    @Override
    public Class<?> loadClass(@NotNull final String s, final boolean resolve, final boolean global, final boolean libraries) throws ClassNotFoundException {
        return loadClass0(s, resolve, global, libraries);
    }

    public synchronized void initialize(@NotNull final JavaPlugin javaPlugin) {
        Preconditions.checkArgument(javaPlugin.getClass().getClassLoader() == this, "Cannot initialize plugin outside of this class loader");
        if (this.plugin == null && this.pluginInit == null) {
            this.pluginState = new IllegalStateException("Initial initialization");
            this.pluginInit = javaPlugin;
            javaPlugin.init(
                (Server) Proxy.newProxyInstance(this, new Class[]{Server.class}, new MockServerInvocationHandler(javaPlugin, Bukkit.getServer())),
                this.description, this.dataFolder.toFile(), new File("/tmp/dummy_jar"), this, this.description, this.logger);
        } else {
            throw new IllegalArgumentException("Plugin already initialized!", this.pluginState);
        }
    }

    @Override
    public void init(final JavaPlugin javaPlugin) {
        initialize(javaPlugin);
    }

    @Override
    public @Nullable JavaPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public @Nullable PluginClassLoaderGroup getGroup() {
        return null;
    }
}
