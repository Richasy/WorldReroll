package dev.richasy.worldreroll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.UUID;
import java.nio.file.StandardOpenOption;

/** Pins one direct child of the saves directory; replacement directories are never deletion targets. */
public record WorldIdentity(Path saves, String id, Path directory, Object fileKey, FileTime created, String token) {
    public static WorldIdentity capture(Path saves, String id) throws IOException {
        if (id.isBlank() || id.equals(".") || id.equals("..") || id.contains("/") || id.contains("\\")
                || id.contains(":") || id.indexOf('\0') >= 0) throw new IOException("Invalid world identifier");
        Path root = saves.toRealPath();
        Path path = root.resolve(id).normalize();
        if (!Objects.equals(path.getParent(), root) || Files.isSymbolicLink(path)) {
            throw new IOException("World must be a direct non-symlink child of saves: " + id);
        }
        Path real = path.toRealPath();
        if (!real.equals(path)) throw new IOException("Redirected world directory: " + id);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) throw new IOException("Not a world directory: " + id);
        // Windows NIO can return a null fileKey. A unique, exclusive marker pins the directory on every platform.
        String token = UUID.randomUUID().toString();
        Files.writeString(real.resolve(".worldreroll-" + token), token, StandardOpenOption.CREATE_NEW);
        return new WorldIdentity(root, id, real, attributes.fileKey(), attributes.creationTime(), token);
    }

    public void verify(Path activeWorld) throws IOException {
        verifyIdentity();
        if (directory.equals(activeWorld.toRealPath())) {
            throw new IOException("World identity changed or still active: " + id);
        }
    }

    public void release() throws IOException {
        verifyIdentity();
        Files.delete(directory.resolve(".worldreroll-" + token));
    }

    private void verifyIdentity() throws IOException {
        Path path = saves.resolve(id).normalize();
        Path marker = path.resolve(".worldreroll-" + token);
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!saves.equals(saves.toRealPath()) || Files.isSymbolicLink(path) || !directory.equals(path.toRealPath())
                || !attributes.isDirectory() || !Objects.equals(fileKey, attributes.fileKey())
                || !created.equals(attributes.creationTime()) || Files.isSymbolicLink(marker)
                || !marker.toRealPath().getParent().equals(directory) || !token.equals(Files.readString(marker))) {
            throw new IOException("World identity changed: " + id);
        }
    }
}
