/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. This file is a port of
 * net.gegy1000.earth.server.world.data.source.reader.TerrariumRasterReader / RasterFormat / RasterFilter.
 */
package dev.esiliw.aquarium.data;

import org.tukaani.xz.SingleXZInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Reads the "TERRARIUM/RASTER" tile format served by the Terrarium geo3 data server. */
public final class TerrariumRasterReader {
    private static final byte[] SIGNATURE = "TERRARIUM/RASTER".getBytes(StandardCharsets.US_ASCII);

    public static final int FORMAT_UBYTE = 0;
    public static final int FORMAT_BYTE = 1;
    public static final int FORMAT_SHORT = 2;

    /** Decoded raster: values stored as int, row-major. */
    public static final class Raster {
        public final int width, height;
        public final int[] values;

        Raster(int width, int height) {
            this.width = width;
            this.height = height;
            this.values = new int[width * height];
        }

        public int get(int x, int y) {
            return this.values[x + y * this.width];
        }
    }

    private TerrariumRasterReader() {
    }

    public static Raster read(InputStream input, int expectedFormat) throws IOException {
        DataInputStream in = new DataInputStream(input);
        byte[] sig = new byte[SIGNATURE.length];
        in.readFully(sig);
        if (!Arrays.equals(sig, SIGNATURE)) throw new IOException("Invalid raster signature");

        int version = in.readUnsignedByte();
        if (version != 0) throw new IOException("Unknown raster version " + version);

        int width = in.readInt();
        int height = in.readInt();
        int format = in.readUnsignedByte();
        if (format != expectedFormat) throw new IOException("Expected raster format " + expectedFormat + " but got " + format);

        Raster raster = new Raster(width, height);
        while (true) {
            int chunkLength;
            try {
                chunkLength = in.readInt();
            } catch (java.io.EOFException e) {
                break;
            }
            byte[] chunk = new byte[chunkLength];
            in.readFully(chunk);
            readChunk(new DataInputStream(new ByteArrayInputStream(chunk)), raster, format);
        }
        return raster;
    }

    private static void readChunk(DataInputStream in, Raster dst, int format) throws IOException {
        int chunkX = in.readInt();
        int chunkY = in.readInt();
        int w = in.readInt();
        int h = in.readInt();
        int filter = in.readUnsignedByte();

        DataInputStream data = new DataInputStream(new SingleXZInputStream(in));
        int[] raw = new int[w * h];
        for (int i = 0; i < raw.length; i++) {
            switch (format) {
                case FORMAT_UBYTE -> raw[i] = data.readUnsignedByte();
                case FORMAT_BYTE -> raw[i] = data.readByte();
                case FORMAT_SHORT -> raw[i] = data.readShort();
                default -> throw new IOException("Unknown raster format " + format);
            }
        }

        // undo the PNG-style prediction filter (values wrap like the original's typed rasters)
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = raw[x + y * w];
                int a = x > 0 ? out[x - 1 + y * w] : 0;
                int b = y > 0 ? out[x + (y - 1) * w] : 0;
                int c = x > 0 && y > 0 ? out[x - 1 + (y - 1) * w] : 0;
                int r = switch (filter) {
                    case 1 -> v + a;
                    case 2 -> v + b;
                    case 3 -> v + (a + b) / 2;
                    case 4 -> {
                        int p = a + b - c;
                        int da = Math.abs(a - p), db = Math.abs(b - p), dc = Math.abs(c - p);
                        yield da < db && da < dc ? v + a : (db < dc ? v + b : v + c);
                    }
                    default -> v;
                };
                out[x + y * w] = wrap(r, format);
            }
        }

        for (int y = 0; y < h; y++) {
            int dy = chunkY + y;
            if (dy < 0 || dy >= dst.height) continue;
            for (int x = 0; x < w; x++) {
                int dx = chunkX + x;
                if (dx < 0 || dx >= dst.width) continue;
                dst.values[dx + dy * dst.width] = out[x + y * w];
            }
        }
    }

    private static int wrap(int value, int format) {
        return switch (format) {
            case FORMAT_UBYTE -> value & 0xFF;
            case FORMAT_BYTE -> (byte) value;
            case FORMAT_SHORT -> (short) value;
            default -> value;
        };
    }
}
