package de.tigges.kaboofcasino.packtool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Deterministic zip: entries in path order, a fixed local time (1980-01-01 00:00, stored as DOS time without the
 * time zone conversion of {@code setTime}), no extra fields, PNG and OGG stored (already compressed), everything else
 * deflated at level 9.
 */
final class ZipOut {

    private static final LocalDateTime FIXED = LocalDateTime.of(1980, 1, 1, 0, 0);

    private ZipOut() {
    }

    static byte[] write(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                String path = e.getKey();
                byte[] data = e.getValue();
                ZipEntry entry = new ZipEntry(path);
                entry.setTimeLocal(FIXED);
                if (path.endsWith(".png") || path.endsWith(".ogg")) {
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(data.length);
                    entry.setCompressedSize(data.length);
                    entry.setCrc(crc.getValue());
                } else {
                    entry.setMethod(ZipEntry.DEFLATED);
                }
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the pack zip", e);
        }
        return bytes.toByteArray();
    }
}
