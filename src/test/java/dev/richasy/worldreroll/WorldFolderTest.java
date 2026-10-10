package dev.richasy.worldreroll;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldFolderTest {
    @TempDir Path saves;

    @Test void readableNameAndCollisionsNeverOverwrite() throws Exception {
        Files.createDirectory(saves.resolve("HelloWorld-reroll-1"));
        Files.writeString(saves.resolve("HelloWorld-reroll-1/keep.txt"), "original");
        Files.writeString(saves.resolve("HelloWorld-reroll-2"), "occupied file");
        assertEquals("HelloWorld-reroll-3", WorldFolder.reserve(saves, "HelloWorld"));
        assertEquals("HelloWorld-reroll-4", WorldFolder.reserve(saves, "HelloWorld"));
        assertEquals("original", Files.readString(saves.resolve("HelloWorld-reroll-1/keep.txt")));
        assertEquals("occupied file", Files.readString(saves.resolve("HelloWorld-reroll-2")));
    }

    @Test void unicodeUnsafeCharactersAndReservedNamesRemainPortable() throws Exception {
        assertEquals("我的世界-reroll-1", WorldFolder.reserve(saves, "我的世界"));
        assertEquals("CON-reroll-1", WorldFolder.reserve(saves, "CON"));
        assertEquals("NUL-reroll-1", WorldFolder.reserve(saves, "NUL"));
        assertEquals("CON_txt-reroll-1", WorldFolder.reserve(saves, "CON.txt"));
        assertEquals("World-reroll-1", WorldFolder.reserve(saves, " ... "));
        String sanitized = WorldFolder.reserve(saves, "../a\\b:c*?\"<>|\u0000");
        assertEquals(saves.toRealPath(), saves.resolve(sanitized).toRealPath().getParent());
        assertFalse(sanitized.matches(".*[\\p{Cntrl}<>:\"/\\\\|?*].*"));
        String longName = WorldFolder.reserve(saves, "玉".repeat(200));
        assertEquals("玉".repeat(48) + "-reroll-1", longName);
    }
}
