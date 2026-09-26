package com.vrushabhhirap.shukracharyascurse.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.Deflater;

/**
 * Archive compression and streaming utilities.
 * Handles GZIP and raw DEFLATE compression for offline cache storage
 * and serialized manuscript blocks.
 */
public final class CompressionHelper {

    private CompressionHelper() {}

    /**
     * Compresses byte array using standard GZIP compression.
     */
    public static byte[] gzipCompress(byte[] data) throws IOException {
        if (data == null || data.length == 0) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
            gzos.finish();
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses GZIP-compressed byte array.
     */
    public static byte[] gzipDecompress(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }

    /**
     * Compresses raw bytes using Deflater at specified compression level.
     */
    public static byte[] deflate(byte[] input, int level) {
        if (input == null) return new byte[0];
        Deflater deflater = new Deflater(level);
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /**
     * Decompresses raw DEFLATE byte array using Inflater.
     */
    public static byte[] inflate(byte[] compressed) throws Exception {
        if (compressed == null) return new byte[0];
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 2);
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) break;
            baos.write(buffer, 0, count);
        }
        inflater.end();
        return baos.toByteArray();
    }
}
