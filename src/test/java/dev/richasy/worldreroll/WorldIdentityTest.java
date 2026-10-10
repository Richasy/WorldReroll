package dev.richasy.worldreroll;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldIdentityTest {
    @TempDir Path saves;

    @Test void exactSourceOnlyAndNeverTheActiveWorld() throws Exception {
        Path old = Files.createDirectory(saves.resolve("old"));
        Path replacement = Files.createDirectory(saves.resolve("new"));
        WorldIdentity identity = WorldIdentity.capture(saves, "old");
        assertDoesNotThrow(() -> identity.verify(replacement));
        assertThrows(IOException.class, () -> identity.verify(old));
        for (String id : new String[]{"", ".", "..", "../outside", "a/b", "a\\b", "C:outside"}) {
            assertThrows(IOException.class, () -> WorldIdentity.capture(saves, id));
        }
    }

    @Test void aReplacedSourceDirectoryIsRetained() throws Exception {
        Path source = Files.createDirectory(saves.resolve("old"));
        Path active = Files.createDirectory(saves.resolve("new"));
        WorldIdentity identity = WorldIdentity.capture(saves, "old");
        Files.move(source, saves.resolve("original-retained"));
        Files.createDirectory(source);
        assertThrows(IOException.class, () -> identity.verify(active));
        assertTrue(Files.exists(saves.resolve("original-retained")));
        assertTrue(Files.exists(source));
    }
}
