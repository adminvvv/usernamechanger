package adminvvv.usernamechanger.config;

import adminvvv.usernamechanger.storage.JsonFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModConfig {
    public int schemaVersion = 1;
    public int requiredOpLevel = 2;
    public boolean allowNonOperators = false;
    public boolean useLuckPerms = true;
    public Map<String, String> messages = defaults();

    public static ModConfig load(Path path) throws IOException {
        ModConfig config = Files.exists(path) ? JsonFiles.read(path, ModConfig.class) : new ModConfig();
        config.validate();
        if (!Files.exists(path)) JsonFiles.write(path, config);
        return config;
    }

    public void validate() throws IOException {
        if (schemaVersion != 1) throw new IOException("Unsupported config schemaVersion: " + schemaVersion);
        if (requiredOpLevel < 1 || requiredOpLevel > 4) throw new IOException("requiredOpLevel must be 1 through 4");
        if (messages == null || messages.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IOException("messages must be an object containing strings");
        }
        defaults().forEach(messages::putIfAbsent);
    }

    public String message(String key, String... replacements) {
        String text = messages.getOrDefault(key, key);
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }

    private static Map<String, String> defaults() {
        var messages = new LinkedHashMap<String, String>();
        messages.put("changed", "{player} is now known as {nickname}.");
        messages.put("reset", "Restored {player}'s account name.");
        messages.put("invalid", "Use 1-16 letters (A-Z), digits, or underscores.");
        messages.put("taken", "That name belongs to another known player or nickname.");
        messages.put("unknown", "Player not found. Use an online player, or a previously seen account name/UUID.");
        messages.put("denied", "You do not have permission to change that player's nickname.");
        messages.put("saveFailed", "Could not save the nickname. No change was applied; check the server log.");
        messages.put("reloaded", "Username Changer configuration reloaded.");
        messages.put("reloadFailed", "Configuration could not be reloaded. The previous configuration is still active.");
        return messages;
    }
}
