package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.service.AccessService.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePortraitStorageTest {
    @TempDir Path directory;
    ProfilePortraitStorage storage;
    @BeforeEach void setup() { storage = new ProfilePortraitStorage(new AppProperties(directory.toString(), 1, null, null, null)); }
    byte[] image(String format, int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, format, bytes); image.flush(); return bytes.toByteArray();
    }
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "../../arbitrary.svg", "application/octet-stream", bytes); }
    Path path(String name) { return directory.resolve("profile-portraits").resolve(name); }

    @Test void jpegAndPngAreDetectedFromContentAndReencodedUnderRandomOwnedKeys() throws Exception {
        for (var format : new String[]{"jpeg", "png"}) {
            var input = new ByteArrayOutputStream(); input.write(image(format, 16, 16)); input.write("<html>extra-payload</html>".getBytes());
            var stored = storage.store(123, file(input.toByteArray()));
            assertTrue(stored.name().startsWith("123_" + stored.version()));
            assertTrue(stored.name().endsWith(format.equals("png") ? ".png" : ".jpg"));
            var read = storage.read(123, stored.name());
            assertEquals(format.equals("png") ? "image/png" : "image/jpeg", read.contentType());
            assertNotNull(ImageIO.read(new ByteArrayInputStream(read.bytes())));
            assertFalse(new String(read.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("extra-payload"));
            assertThrows(NotFoundException.class, () -> storage.read(456, stored.name()));
            storage.deleteQuietly(456, stored.name()); assertTrue(Files.exists(path(stored.name())));
            storage.deleteQuietly(123, stored.name()); assertFalse(Files.exists(path(stored.name())));
        }
    }

    @Test void purgeDeletesAllExactIdPrefixFilesButNeverOtherUsersOrSharedMedia() throws Exception {
        var current = storage.store(12, file(image("png", 2, 2)));
        var historical = storage.store(12, file(image("jpeg", 2, 2)));
        var other = storage.store(123, file(image("png", 2, 2)));
        var orphan = path("12_legacy-orphan.bin"); Files.writeString(orphan, "private");
        var shared = directory.resolve("12_shared-family-media.png"); Files.writeString(shared, "keep");
        storage.purge(12);storage.purge(12);
        assertFalse(Files.exists(path(current.name())));assertFalse(Files.exists(path(historical.name())));assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(path(other.name())));assertEquals("keep", Files.readString(shared));
    }

    @Test void purgeFailsVisiblyAndCanBeRetriedWithoutRecursing() throws Exception {
        storage.purge(12); // Missing storage directory means no files, not an error.
        Files.createDirectories(path("12_unexpected-directory"));
        Files.writeString(path("12_unexpected-directory").resolve("keep"), "do not recurse");
        assertThrows(ProfilePortraitStorage.PortraitPurgeException.class, () -> storage.purge(12));
        assertTrue(Files.exists(path("12_unexpected-directory").resolve("keep")));
        Files.delete(path("12_unexpected-directory").resolve("keep")); Files.delete(path("12_unexpected-directory"));
        storage.purge(12);
    }

    @Test void dimensionsAndTotalPixelBudgetAreCheckedBeforeFullDecode() throws Exception {
        // Change just PNG IHDR dimensions and its CRC. No huge pixel buffer is allocated by this test.
        for (int[] dimensions : new int[][]{{4097,1}, {1,4097}, {3000,3000}, {Integer.MAX_VALUE,Integer.MAX_VALUE}}) {
            byte[] bytes = image("png", 1, 1);
            ByteBuffer.wrap(bytes).putInt(16, dimensions[0]).putInt(20, dimensions[1]);
            var crc = new CRC32(); crc.update(bytes, 12, 17); ByteBuffer.wrap(bytes).putInt(29, (int)crc.getValue());
            assertThrows(BadRequestException.class, () -> storage.store(123, file(bytes)));
        }
        assertFalse(Files.exists(directory.resolve("profile-portraits")));
    }

    @Test void invalidSignatureCorruptDecodeAndByteBudgetNeverCreateFiles() {
        for (byte[] data : new byte[][]{new byte[0], new byte[]{1,2,3}, new byte[]{(byte)255,(byte)216,(byte)255,0},
                "GIF89a".getBytes(), "RIFFxxxxWEBP".getBytes(), "<svg/>".getBytes(), "%PDF-1.5".getBytes(), new byte[ProfilePortraitStorage.MAX_BYTES + 1]}) {
            assertThrows(BadRequestException.class, () -> storage.store(123, file(data)));
        }
        assertFalse(Files.exists(directory.resolve("profile-portraits")));
    }

    @Test void pathTraversalCannotBeReadOrDeletedAndWriteFailuresLeaveNoPartialImage() throws Exception {
        Path sentinel = directory.resolve("sentinel.png"); Files.writeString(sentinel, "keep");
        for (String key : new String[]{"../sentinel.png", sentinel.toString(), "123_../../sentinel.png", "456_00000000-0000-0000-0000-000000000000.png"}) {
            assertThrows(NotFoundException.class, () -> storage.read(123, key));
            storage.deleteQuietly(123, key); assertEquals("keep", Files.readString(sentinel));
        }
        // A storage directory replaced by a regular file is rejected without touching it.
        Files.deleteIfExists(directory.resolve("profile-portraits"));
        Files.writeString(directory.resolve("profile-portraits"), "not-a-directory");
        assertThrows(IllegalStateException.class, () -> storage.store(123, file(image("png", 4, 4))));
        assertEquals("not-a-directory", Files.readString(directory.resolve("profile-portraits")));
    }
}
