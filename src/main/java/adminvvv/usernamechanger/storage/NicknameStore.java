package adminvvv.usernamechanger.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class NicknameStore {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Path path;
    private volatile Map<UUID, Entry> players;

    public record Entry(String accountName, String nickname) {}
    private record Data(int schemaVersion, Map<UUID, Entry> players) {}

    public NicknameStore(Path path) throws IOException {
        this.path = path;
        Data data = Files.exists(path) ? JsonFiles.read(path, Data.class) : new Data(1, Map.of());
        if (data.schemaVersion != 1 || data.players == null) throw new IOException("Invalid nickname storage schema");
        for (var item : data.players.entrySet()) {
            Entry entry = item.getValue();
            if (item.getKey() == null || entry == null || !validAccountName(entry.accountName)) {
                throw new IOException("Invalid account record for UUID " + item.getKey() + " in " + path);
            }
        }
        players = Map.copyOf(data.players);
        players.forEach((id, entry) -> {
            if (entry.nickname != null && nickname(id) == null) {
                org.slf4j.LoggerFactory.getLogger(NicknameStore.class).warn(
                        "Nickname for {} is invalid or conflicts with another player; using account name. "
                                + "Stored data is preserved. Use /usernamereset {} to clear it.", id, id);
            }
        });
    }

    public static boolean valid(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public static boolean validAccountName(String name) {
        return name != null && !name.isBlank() && name.codePoints().noneMatch(Character::isISOControl);
    }

    public Map<UUID, Entry> entries() { return players; }

    public String nickname(UUID id) {
        Entry entry = players.get(id);
        if (entry == null || !valid(entry.nickname)) return null;
        // A real account wins if its owner joins after the alias was assigned.
        for (var other : players.entrySet()) {
            if (!other.getKey().equals(id) && (other.getValue().accountName.equalsIgnoreCase(entry.nickname)
                    || entry.nickname.equalsIgnoreCase(other.getValue().nickname))) return null;
        }
        return entry.nickname;
    }

    public UUID resolve(String name) {
        try {
            UUID id = UUID.fromString(name);
            if (players.containsKey(id)) return id;
        } catch (IllegalArgumentException ignored) {}
        UUID accountOwner = null;
        for (var item : players.entrySet()) {
            if (item.getValue().accountName.equalsIgnoreCase(name)) {
                if (accountOwner != null) return null;
                accountOwner = item.getKey();
            }
        }
        if (accountOwner != null) return accountOwner;
        for (UUID id : players.keySet()) {
            String alias = nickname(id);
            if (alias != null && alias.equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    public boolean available(UUID owner, String nickname) {
        return players.entrySet().stream().noneMatch(item -> !item.getKey().equals(owner)
                && (item.getValue().accountName.equalsIgnoreCase(nickname)
                || nickname.equalsIgnoreCase(item.getValue().nickname)));
    }

    public void remember(UUID id, String accountName) throws IOException {
        if (id == null || !validAccountName(accountName)) throw new IllegalArgumentException("Invalid account identity");
        Entry old = players.get(id);
        if (old != null && old.accountName.equals(accountName)) return;
        commit(id, new Entry(accountName, old == null ? null : old.nickname));
    }

    public int importUserCache(Path cachePath) throws IOException {
        if (!Files.exists(cachePath)) return 0;
        var records = JsonFiles.read(cachePath, com.google.gson.JsonElement[].class);
        var next = new LinkedHashMap<>(players);
        int skipped = 0;
        for (var record : records) {
            try {
                if (record == null || !record.isJsonObject()) throw new IllegalArgumentException("Invalid cache record");
                var object = record.getAsJsonObject();
                var uuid = object.getAsJsonPrimitive("uuid");
                var name = object.getAsJsonPrimitive("name");
                if (uuid == null || name == null || !uuid.isString() || !name.isString()) {
                    throw new IllegalArgumentException("Missing account identity");
                }
                UUID id = UUID.fromString(uuid.getAsString());
                if (!id.toString().equalsIgnoreCase(uuid.getAsString()) || !validAccountName(name.getAsString())) {
                    throw new IllegalArgumentException("Invalid account identity");
                }
                // Cache entries can be stale; an identity already observed by the mod takes priority.
                next.putIfAbsent(id, new Entry(name.getAsString(), null));
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException e) {
                skipped++;
            }
        }
        if (skipped > 0) org.slf4j.LoggerFactory.getLogger(NicknameStore.class)
                .warn("Skipped {} invalid user cache records in {}", skipped, cachePath);
        int imported = next.size() - players.size();
        if (imported > 0) {
            JsonFiles.write(path, new Data(1, next));
            players = Map.copyOf(next);
        }
        return imported;
    }

    public java.util.Collection<String> currentNames(java.util.Set<UUID> onlinePlayers) {
        var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        players.forEach((id, entry) -> {
            String alias = nickname(id);
            if (alias == null || onlinePlayers.contains(id)) names.add(alias == null ? entry.accountName : alias);
        });
        return java.util.List.copyOf(names);
    }

    public void set(UUID id, String nickname) throws IOException {
        Entry old = players.get(id);
        if (old == null) throw new IllegalArgumentException("Unknown player");
        if (nickname != null && (!valid(nickname) || !available(id, nickname))) {
            throw new IllegalArgumentException("Invalid or unavailable nickname");
        }
        commit(id, new Entry(old.accountName, nickname));
    }

    private void commit(UUID id, Entry entry) throws IOException {
        var next = new LinkedHashMap<>(players);
        next.put(id, entry);
        JsonFiles.write(path, new Data(1, next));
        players = Map.copyOf(next);
    }
}
