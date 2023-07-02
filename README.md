# Cabinet

Cabinet is a Paper plugin to create self-extracting, self-contained meta-plugins called **Cabinettes.**

Cabinettes are plugins that contain other plugins and related files. When a Cabinette is enabled, it extracts resources into its data folder and loads any plugins it contains.

## Usage

### Creating a Cabinette

To create a Cabinette, use the `/cabinet` command, followed by the name of the Cabinette, and then each file to be included in the Cabinette.

For example, to create a Cabinette called `Example`, which contains the plugins `PluginA.jar`, `PluginA/config.yml`, and `PluginB.jar`, use this command:

```mcfunction
/cabinet Example PluginA.jar PluginA/config.yml PluginB.jar
```

When the command is executed, Cabinet will create a Cabinette called `Example.jar` in the `plugins` folder. This Cabinette can be distributed and installed like any other plugin.

> **Warning**  
> Please be mindful of the respective licences of the files you include in your Cabinette; Distributing them is illegal unless you are explicitly permitted to do so by the copyright holders.