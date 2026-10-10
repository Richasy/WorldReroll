package dev.richasy.worldreroll;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reserves a readable direct-child directory without opening or replacing an existing save. */
public final class WorldFolder {
    private WorldFolder() {}

    public static String reserve(Path saves, String worldName) throws IOException {
        Path root = saves.toRealPath();
        String base = worldName.strip().replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                .replaceAll("[ .]+$", "").replace('.', '_');
        if (base.isBlank()) base = "World";
        if (base.codePointCount(0, base.length()) > 48) base = base.substring(0, base.offsetByCodePoints(0, 48));
        // The suffix also makes Windows device names such as CON and NUL portable.
        for (int number = 1; number <= 10000; number++) {
            String id = base + "-reroll-" + number;
            try {
                Files.createDirectory(root.resolve(id));
                return id;
            } catch (FileAlreadyExistsException occupied) {
                // Reserve the next number, including when a file or link occupies this name.
            }
        }
        throw new IOException("No available reroll directory for world " + worldName);
    }
}
