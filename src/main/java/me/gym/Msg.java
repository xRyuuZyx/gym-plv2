package me.gym;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {
    }

    public static Component c(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** Ambil pesan dari config, lalu ganti placeholder (pasangan key, value). */
    public static String raw(String key, String... repl) {
        String s = Gym.get().getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < repl.length; i += 2) {
            s = s.replace(repl[i], repl[i + 1]);
        }
        return s;
    }

    public static void send(CommandSender to, String key, String... repl) {
        to.sendMessage(c(raw("prefix") + raw(key, repl)));
    }

    public static void sendPlain(CommandSender to, String key, String... repl) {
        to.sendMessage(c(raw(key, repl)));
    }
}
