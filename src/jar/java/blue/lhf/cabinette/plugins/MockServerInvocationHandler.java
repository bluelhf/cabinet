package blue.lhf.cabinette.plugins;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.*;

public class MockServerInvocationHandler implements InvocationHandler {

    private final Server server;
    private final Plugin plugin;

    public MockServerInvocationHandler(final Plugin plugin, final Server server) {
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getName().equals("getPluginCommand")) {
            final String name = (String) args[0];
            final Constructor<PluginCommand> constr = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constr.setAccessible(true);
            return constr.newInstance(name, plugin);
        }

        return method.invoke(server, args);
    }
}
