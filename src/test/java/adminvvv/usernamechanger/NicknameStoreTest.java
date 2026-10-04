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

    @Test void floodgateAccountsSurviveRestartWithoutChangingTheirIdentity() throws IOException {
        Path path = directory.resolve("names.json");
        var store = new NicknameStore(path);
        store.remember(alice, ".Bedrock_Player123");
        store.remember(bob, "*Bedrock Player");
        store.set(alice, "Comet");
        store = new NicknameStore(path);
        assertEquals("Comet", store.nickname(alice));
        assertEquals(alice, store.resolve(".Bedrock_Player123"));
        assertEquals(bob, store.resolve("*Bedrock Player"));
        assertEquals("*Bedrock Player", store.entries().get(bob).accountName());
    }

    @Test void invalidAndDuplicateNicknamesAreSuspendedWithoutEditingStorage() throws IOException {
        Path path = directory.resolve("names.json");
        String duplicate = "{\"schemaVersion\":1,\"players\":{\"" + alice
                + "\":{\"accountName\":\".Alice\",\"nickname\":\"Comet\"},\"" + bob
                + "\":{\"accountName\":\"Bob\",\"nickname\":\"COMET\"}}}";
        Files.writeString(path, duplicate);
        var store = new NicknameStore(path);
        assertNull(store.nickname(alice));
        assertNull(store.nickname(bob));
        assertNull(store.resolve("Comet"));
        assertEquals(duplicate, Files.readString(path));
        store.set(bob, null);
        assertEquals("Comet", new NicknameStore(path).nickname(alice));
        String invalid = duplicate.replace("Comet", "invalid nickname").replace("COMET", "Valid");
        Files.writeString(path, invalid);
        store = new NicknameStore(path);
        assertNull(store.nickname(alice));
        assertEquals("Valid", store.nickname(bob));
        assertEquals(invalid, Files.readString(path));
    }

    @Test void importsMissingCachedPlayersWithoutOverwritingKnownIdentities() throws IOException {
        Path path = directory.resolve("names.json");
        Path cache = directory.resolve("usercache.json");
        var store = new NicknameStore(path);
        store.remember(alice, "Alice");
        store.set(alice, "Comet");
        String contents = "[{\"uuid\":\"" + alice + "\",\"name\":\"OldAlice\"},"
                + "{\"uuid\":\"" + bob + "\",\"name\":\".Bedrock Player\",\"expiresOn\":\"2000-01-01 00:00:00 +0000\"},"
                + "{\"uuid\":\"" + bob + "\",\"name\":\"Duplicate\"},null,{},42,"
                + "{\"uuid\":\"invalid\",\"name\":\"Broken\"}]";
        Files.writeString(cache, contents);
        assertEquals(1, store.importUserCache(cache));
        assertEquals(contents, Files.readString(cache));
        assertEquals("Alice", store.entries().get(alice).accountName());
        assertEquals("Comet", store.nickname(alice));
        assertEquals(bob, store.resolve(".Bedrock Player"));
        assertEquals(java.util.List.of(".Bedrock Player"), store.currentNames(java.util.Set.of()));
        String persisted = Files.readString(path);
        assertEquals(0, store.importUserCache(cache));
        assertEquals(persisted, Files.readString(path));
        store = new NicknameStore(path);
        store.set(bob, "BedrockNick");
        assertEquals("BedrockNick", new NicknameStore(path).nickname(bob));
    }

    @Test void absentOrMalformedCacheDoesNotDamageExistingStorage() throws IOException {
        Path path = directory.resolve("names.json");
        Path cache = directory.resolve("usercache.json");
        var store = new NicknameStore(path);
        store.remember(alice, "Alice");
        String persisted = Files.readString(path);
        assertEquals(0, store.importUserCache(cache));
        for (String invalid : new String[]{"{broken", "{}", "null"}) {
            Files.writeString(cache, invalid);
            assertThrows(IOException.class, () -> store.importUserCache(cache));
            assertEquals(persisted, Files.readString(path));
            assertEquals(1, store.entries().size());
        }
    }

    @Test void cacheImportFailureDoesNotPublishUnsavedPlayers() throws IOException {
        Path path = directory.resolve("names.json");
        Path cache = directory.resolve("usercache.json");
        var store = new NicknameStore(path);
        store.remember(alice, "Alice");
        Files.writeString(cache, "[{\"uuid\":\"" + bob + "\",\"name\":\"Bob\"}]");
        Files.delete(path);
        Files.createDirectory(path);
        Files.writeString(path.resolve("blocker"), "occupied");
        assertThrows(IOException.class, () -> store.importUserCache(cache));
        assertNull(store.resolve("Bob"));
        assertEquals(1, store.entries().size());
    }

    @Test void suggestionsIncludeCurrentOnlineNamesAndOnlyUnnamedOfflinePlayers() throws IOException {
        var store = new NicknameStore(directory.resolve("names.json"));
        store.remember(alice, "Alice");
        store.remember(bob, ".Bob");
        store.set(alice, "Comet");
        assertEquals(java.util.Set.of("Comet", ".Bob"), java.util.Set.copyOf(store.currentNames(java.util.Set.of(alice))));
        assertEquals(java.util.List.of(".Bob"), store.currentNames(java.util.Set.of()));
        store.set(alice, "Meteor");
        assertFalse(store.currentNames(java.util.Set.of(alice)).contains("Comet"));
        assertFalse(store.currentNames(java.util.Set.of(alice)).contains("Alice"));
        assertEquals(alice, store.resolve("Meteor"));
        store.set(alice, null);
        assertTrue(store.currentNames(java.util.Set.of()).contains("Alice"));
    }
}
