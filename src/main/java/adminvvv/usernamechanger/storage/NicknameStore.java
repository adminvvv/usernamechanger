package adminvvv.usernamechanger.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
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
        var aliases = new java.util.HashSet<String>();
        for (var item : data.players.entrySet()) {
            Entry entry = item.getValue();
            if (item.getKey() == null || entry == null || !valid(entry.accountName)
                    || (entry.nickname != null && (!valid(entry.nickname)
                    || !aliases.add(entry.nickname.toLowerCase(Locale.ROOT))))) {
                throw new IOException("Invalid or duplicated nickname record in " + path);
            }
        }
        players = Map.copyOf(data.players);
    }

    public static boolean valid(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public Map<UUID, Entry> entries() { return players; }

    public String nickname(UUID id) {
        Entry entry = players.get(id);
        if (entry == null || entry.nickname == null) return null;
        // A real account wins if its owner joins after the alias was assigned.
        for (var other : players.entrySet()) {
            if (!other.getKey().equals(id) && other.getValue().accountName.equalsIgnoreCase(entry.nickname)) return null;
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
        Entry old = players.get(id);
        if (old != null && old.accountName.equals(accountName)) return;
        commit(id, new Entry(accountName, old == null ? null : old.nickname));
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
