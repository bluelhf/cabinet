Terminology
==========

**Cabinet** refers to the main plugin, which is used to create cabinettes.

**Cabinette** refers to a plugin JAR created by Cabinet.

# This Document

The purpose of this document is to describe the general process of Cabinet, and provide reasoning for some of the design decisions made.

It is intended mostly as a note to self, but may be useful to others wishing to understand the inner workings of Cabinet.

---

## Cabinette Class Shenanigans

Cabinet consists of two source directories,`main` and `jar`, achieved with `build-helper-plugin`. The `main` directory contains code related to the Cabinet plugin itself, which is used for creating cabinettes. The`jar` directory contains code to be injected into cabinet plugins while being built by Cabinet.

Initially, both source directories are compiled into a shared target directory,`target/classes`. However, after compilation, the classes in the `jar` directory are moved into the `inject` subdirectory.

The purpose of this separation is to fix a problem brought by Spigot's plugin loader mechanism; if one plugin contains the main class of another plugin in its JAR, the plugin loader will load the incorrect class and fail. To avoid this, Cabinet places .class files to be injected into the `inject` subdirectory within its own JAR, while maintaining the original package declarations.

To move the files, we use `maven-antrun-plugin`.
`maven-antrun-plugin` is included as a post-processing step for class files, which moves the files from `blue/lhf/cabinette/` into the `inject` directory.

## Cabinette Extraction

All files to be extracted are included under `files` in Cabinette JARs. These files are extracted blindly into the data folder of the Cabinette when it is enabled.

### Special Handling: Plugins

Some files included in Cabinettes may be plugins. These are extracted into the data folder as normal, but extraction is followed by a plugin bootstrap process.

1. Each plugin is registered with the Cabinette Plugin Bootstrap when it is extracted.
2. When the Cabinette is enabled, the Cabinette Plugin Bootstrap loads all registered plugins.
    1. It first builds a dependency graph of all registered plugins, then traverses it topographically, loading and enabling each plugin in order. This way, the dependencies of plugins are always loaded before the plugins themselves.
   2. Plugin loading is handled by the Embedded Loader, which consists of a custom plugin loader, library loader, and class loader.

## The Embedded Loader

The Embedded Loader is mostly copied from the Spigot plugin loader mechanism, with some occasional cleanup. Only two noteworthy modifications are made:

1. The parent class loader of the plugin class loader
   is set to the server's class loader, not that of
   the plugin loader.
2. The JavaPlugin initialisation process is modified
   to use a reflective proxy of the Server object
   instead of the actual Server object.

   For the most part, this reflective proxy simply passes method calls to the actual Server object. However, in the case of methods such as `getPluginCommand`, a no-op object is returned instead, so that plugins included in Cabinette do not register server commands.

### Stormy Skies on the Horizon
Paper is currently working on a new plugin loader mechanism. This mechanism is partially incompatible with Spigot's, and as Spigot's methods are slowly deprecated and removed, Cabinet will cease to function. We'll likely address this by making Cabinet a Paper plugin or implementing two separate plugin loaders, one for Spigot and one for Paper.

