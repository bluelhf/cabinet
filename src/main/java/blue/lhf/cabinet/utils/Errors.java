package blue.lhf.cabinet.utils;

import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.*;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class Errors {
    private Errors() {

    }

    public static void reportError(final CommandSender sender, final Throwable error, final String action) {
        sender.sendMessage(text("Something went wrong while " + action, RED));
        final StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        if (sender instanceof Player) {
            sender.sendMessage(text().color(RED)
                                   .append(text("Place your mouse cursor"))
                                   .append(text(" here", WHITE).hoverEvent(HoverEvent.showText(text(sw.toString().replace("\t", "    ")))))
                                   .append(text(" for details")));
        } else {
            sw.toString().replace("\t", "    ").lines().forEach(line -> sender.sendMessage(text(line, RED)));
        }
    }
}
