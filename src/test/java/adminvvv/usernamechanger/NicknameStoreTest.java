package adminvvv.usernamechanger;

import adminvvv.usernamechanger.storage.NicknameStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NicknameStoreTest {
    @TempDir Path directory;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test void persistsAcrossRestartAccountRenameAndReset() throws IOException {
        Path path = directory.resolve("names.json");
        var store = new NicknameStore(path);
        store.remember(alice, "Alice");
        store.set(alice, "Comet");
        store = new NicknameStore(path);
        assertEquals("Comet", store.nickname(alice));
        assertEquals(alice, store.resolve("cOmEt"));
        assertEquals(alice, store.resolve("ALICE"));
        assertEquals(alice, store.resolve(alice.toString()));
        store.remember(alice, "AliceRenamed");
        assertEquals("Comet", new NicknameStore(path).nickname(alice));
        store.set(alice, null);
        assertNull(new NicknameStore(path).nickname(alice));
    }

    @Test void rejectsDuplicateAliasesAndKnownAccountNames() throws IOException {
        var store = new NicknameStore(directory.resolve("names.json"));
        store.remember(alice, "Alice");
        store.remember(bob, "Bob");
        store.set(alice, "Comet");
        assertFalse(store.available(bob, "COMET"));
        assertFalse(store.available(bob, "ALIce"));
        assertThrows(IllegalArgumentException.class, () -> store.set(bob, "comet"));
        assertTrue(store.available(alice, "Comet"));
    }

    @Test void realAccountJoiningSuspendsConflictingAlias() throws IOException {
        var store = new NicknameStore(directory.resolve("names.json"));
        store.remember(alice, "Alice");
        store.set(alice, "Bob");
        store.remember(bob, "Bob");
        assertNull(store.nickname(alice));
        assertEquals(bob, store.resolve("Bob"));
        assertEquals("Bob", store.entries().get(alice).nickname());
    }

    @Test void invalidStorageIsPreserved() throws IOException {
        Path path = directory.resolve("names.json");
        Files.writeString(path, "{broken");
        assertThrows(IOException.class, () -> new NicknameStore(path));
        assertEquals("{broken", Files.readString(path));
    }

    @Test void reusedAccountNamesRequireAnUnambiguousUuid() throws IOException {
        var store = new NicknameStore(directory.resolve("names.json"));
        store.remember(alice, "ReusedName");
        store.remember(bob, "ReusedName");
        assertNull(store.resolve("ReusedName"));
        assertEquals(alice, store.resolve(alice.toString()));
        assertEquals(bob, store.resolve(bob.toString()));
    }

    @Test void failedWriteDoesNotChangeMemory() throws IOException {
        Path path = directory.resolve("names.json");
        var store = new NicknameStore(path);
        store.remember(alice, "Alice");
        Files.delete(path);
        Files.createDirectory(path);
        Files.writeString(path.resolve("blocker"), "occupied");
        assertThrows(IOException.class, () -> store.set(alice, "Comet"));
        assertNull(store.nickname(alice));
    }

    @Test void namesFitVanillaProfilesAndCommands() {
        assertTrue(NicknameStore.valid("A_123"));
        assertTrue(NicknameStore.valid("1234567890123456"));
        for (String invalid : new String[]{"", "with space", "@a", "a/b", "12345678901234567", "a\n"}) {
            assertFalse(NicknameStore.valid(invalid), invalid);
        }
    }
}
