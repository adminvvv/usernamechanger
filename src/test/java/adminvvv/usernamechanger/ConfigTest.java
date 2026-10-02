package adminvvv.usernamechanger;

import adminvvv.usernamechanger.config.ModConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path directory;

    @Test void defaultsAreRestrictedAndMessagesCanBeTranslated() throws IOException {
        var config = ModConfig.load(directory.resolve("config.json"));
        assertFalse(config.allowNonOperators);
        assertEquals(2, config.requiredOpLevel);
        config.messages.put("changed", "{player} -> {nickname}");
        assertEquals("Alice -> Comet", config.message("changed", "player", "Alice", "nickname", "Comet"));
    }

    @Test void badPermissionConfigurationFailsClosed() throws IOException {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{\"requiredOpLevel\":0}");
        assertThrows(IOException.class, () -> ModConfig.load(path));
        Files.writeString(path, "{\"messages\":null}");
        assertThrows(IOException.class, () -> ModConfig.load(path));
    }
}
