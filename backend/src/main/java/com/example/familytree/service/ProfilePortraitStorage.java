package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.service.AccessService.BadRequestException;
import com.example.familytree.service.AccessService.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/** Private storage, deliberately unrelated to family media and original filenames. */
@Component
public class ProfilePortraitStorage {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 8_000_000;
    private static final int MAX_DIMENSION = 4096;
    private static final Logger log = LoggerFactory.getLogger(ProfilePortraitStorage.class);
    private final Path root;

    public ProfilePortraitStorage(AppProperties properties) {
        root = Path.of(properties.storageRoot()).toAbsolutePath().normalize().resolve("profile-portraits");
    }

    public record Stored(String name, String version) {}
    public record Portrait(byte[] bytes, String contentType) {}
    private record Decoded(BufferedImage image, String format) {}

    public Stored store(long userId, MultipartFile file) {
        var decoded = decode(file);
        var version = UUID.randomUUID().toString();
        var name = userId + "_" + version + (decoded.format().equals("png") ? ".png" : ".jpg");
        Path target = null;
        boolean created = false;
        try {
            target = ownPath(userId, name);
            // CREATE_NEW and NOFOLLOW_LINKS prevent accidental overwrite and symlink following.
            try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                if (!ImageIO.write(decoded.image(), decoded.format(), out)) throw new IOException("Encoder unavailable");
            }
            return new Stored(name, version);
        } catch (IOException e) {
            if (created) deleteQuietly(userId, name);
            throw new IllegalStateException("头像保存失败");
        } finally {
            decoded.image().flush();
        }
    }

    private Decoded decode(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid();
        try {
            byte[] bytes;
            try (var in = file.getInputStream()) { bytes = in.readNBytes(MAX_BYTES + 1); }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
            String format;
            if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) format = "png";
            else if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) format = "jpeg";
            else throw invalid();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    if (!reader.getFormatName().equalsIgnoreCase(format)) throw invalid();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    // Check dimensions before decoding compressed pixels (decompression bomb protection).
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) throw invalid();
                    var source = reader.read(0);
                    if (source == null) throw invalid();
                    try {
                        // Fresh raster + default encoder: discard metadata, trailing payload and ancillary chunks.
                        var clean = new BufferedImage(width, height, format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = clean.createGraphics();
                        try { graphics.drawImage(source, 0, 0, null); } finally { graphics.dispose(); }
                        return new Decoded(clean, format);
                    } finally { source.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    public Portrait read(long userId, String name) {
        if (name == null) throw new NotFoundException("尚未设置头像");
        try {
            var path = ownPath(userId, name);
            try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                 var input = Channels.newInputStream(channel)) {
                return new Portrait(input.readAllBytes(), name.endsWith(".png") ? "image/png" : "image/jpeg");
            }
        } catch (IOException e) { throw new NotFoundException("头像不存在"); }
    }

    private Path ownPath(long userId, String name) throws IOException {
        if (name == null || !name.matches(userId + "_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)")) {
            throw new IOException("Invalid private storage key");
        }
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid storage directory");
        var target = root.resolve(name).normalize();
        if (!target.getParent().equals(root)) throw new IOException("Invalid storage path");
        return target;
    }

    /** Remove every private image for this id, including replaced/rollback orphan files.
     * Never recurse, follow links, use a client filename, or touch family media.
     * Errors are deliberately propagated so DELETE can be retried after its DB commit.
     */
    public void purge(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("Invalid user id");
        try {
            if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return;
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid storage directory");
            IOException failure = null;
            try (var files = Files.newDirectoryStream(root, userId + "_*")) {
                for (var file : files) {
                    try {
                        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected private directory");
                        Files.deleteIfExists(file); // Unlinks symlinks themselves, never their targets.
                    } catch (IOException e) {
                        if (failure == null) failure = e;
                        else failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) throw failure;
        } catch (IOException | RuntimeException e) {
            log.warn("Private portrait purge was not completed; retry account deletion");
            throw new PortraitPurgeException();
        }
    }

    public static class PortraitPurgeException extends RuntimeException {
        public PortraitPurgeException() { super("账号已匿名化并撤销访问，但私人头像清理未完成，请重试删除同一账号 ID"); }
    }

    public void deleteQuietly(long userId, String name) {
        if (name == null) return;
        try { Files.deleteIfExists(ownPath(userId, name)); }
        catch (IOException | RuntimeException e) {
            // No identity, client filename, filesystem path or image content in logs.
            log.warn("Private portrait cleanup was not completed");
        }
    }

    private BadRequestException invalid() {
        return new BadRequestException("头像必须是有效的 JPEG/PNG，大小不超过 5 MiB，单边不超过 4096 像素且总像素不超过 800 万");
    }
}
