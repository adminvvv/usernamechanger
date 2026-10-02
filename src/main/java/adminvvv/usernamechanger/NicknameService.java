package adminvvv.usernamechanger;

import adminvvv.usernamechanger.config.ModConfig;
import adminvvv.usernamechanger.compat.PlayerPresentation;
import adminvvv.usernamechanger.permissions.CommandPermissions;
import adminvvv.usernamechanger.storage.NicknameStore;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class NicknameService {
    private final MinecraftServer server;
    private final Path configPath;
    private ModConfig config;
    private final NicknameStore store;
    private final CommandPermissions permissions = new CommandPermissions();

    public NicknameService(MinecraftServer server, Path configPath, ModConfig config) throws IOException {
        this.server = server;
        this.configPath = configPath;
        this.config = config;
        store = new NicknameStore(server.getWorldPath(LevelResource.ROOT).resolve("usernamechanger.json"));
    }

    public String nickname(UUID id) { return store.nickname(id); }
    public String visibleName(ServerPlayer player) {
        String nickname = nickname(player.getUUID());
        return nickname == null ? player.getGameProfile().name() : nickname;
    }

    public GameProfile clientProfile(GameProfile original) {
        String name = nickname(original.id());
        return name == null ? original : new GameProfile(original.id(), name, original.properties());
    }

    public String teamEntry(String original) {
        UUID id = store.resolve(original);
        if (id != null && store.entries().get(id).accountName().equals(original)) {
            String nickname = nickname(id);
            return nickname == null ? original : nickname;
        }
        return original;
    }

    public void joining(ServerPlayer player) throws IOException {
        Map<ServerPlayer, String> before = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) before.put(online, visibleName(online));
        store.remember(player.getUUID(), player.getGameProfile().name());
        before.forEach((online, oldName) -> {
            if (!oldName.equals(visibleName(online))) PlayerPresentation.refresh(server, online, oldName);
        });
    }

    public ServerPlayer onlineAlias(String name) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String nickname = nickname(player.getUUID());
            if (nickname != null && nickname.equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    public net.minecraft.server.players.NameAndId profileTarget(String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new net.minecraft.server.players.NameAndId(online.getGameProfile());
        UUID id = store.resolve(name);
        return id == null ? null : new net.minecraft.server.players.NameAndId(id, store.entries().get(id).accountName());
    }

    public String[] suggestions(String[] originals) {
        var names = new LinkedHashSet<String>(java.util.List.of(originals));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) names.add(visibleName(player));
        return names.toArray(String[]::new);
    }

    public java.util.Collection<String> knownNames() {
        var names = new LinkedHashSet<String>();
        store.entries().forEach((id, entry) -> {
            names.add(entry.accountName());
            String nickname = nickname(id);
            if (nickname != null) names.add(nickname);
        });
        return names;
    }

    public boolean allowed(CommandSourceStack source, String action) {
        return permissions.allowed(source, config, action);
    }

    public Component message(String key, String... replacements) {
        return Component.literal(config.message(key, replacements));
    }

    public int change(CommandSourceStack source, String target, String nickname) {
        if (!allowed(source, "change")) return fail(source, "denied");
        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(target);
        UUID id = onlineTarget == null ? store.resolve(target) : onlineTarget.getUUID();
        if (id == null) return fail(source, "unknown");
        if (nickname != null && !NicknameStore.valid(nickname)) return fail(source, "invalid");
        if (nickname != null && !store.available(id, nickname)) return fail(source, "taken");
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        String account = store.entries().get(id).accountName();
        String oldVisible = player == null ? teamEntry(account) : visibleName(player);
        try {
            store.set(id, nickname);
        } catch (IOException e) {
            Usernamechanger.LOGGER.error("Could not persist nickname for {}", id, e);
            return fail(source, "saveFailed");
        }
        if (player != null) PlayerPresentation.refresh(server, player, oldVisible);
        else PlayerPresentation.refreshTeam(server, account, oldVisible, teamEntry(account));
        source.sendSuccess(() -> nickname == null ? message("reset", "player", account)
                : message("changed", "player", account, "nickname", nickname), true);
        Usernamechanger.LOGGER.info("{} changed nickname for {} ({}) to {}", source.getTextName(), account, id, nickname);
        return 1;
    }

    public int reload(CommandSourceStack source) {
        if (!allowed(source, "reload")) return fail(source, "denied");
        try {
            ModConfig next = ModConfig.load(configPath);
            config = next;
        } catch (IOException | RuntimeException e) {
            Usernamechanger.LOGGER.error("Could not reload configuration", e);
            return fail(source, "reloadFailed");
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
        source.sendSuccess(() -> message("reloaded"), false);
        return 1;
    }

    private int fail(CommandSourceStack source, String key) {
        source.sendFailure(message(key));
        return 0;
    }
}
