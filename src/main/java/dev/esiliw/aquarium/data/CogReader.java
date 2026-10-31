/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Minimal reader for single-band 8-bit cloud-optimised GeoTIFFs (tiled, with overviews), fetched piecewise over HTTP.
 * Supports classic TIFF and BigTIFF, compression none / LZW / Deflate, horizontal predictor.
 */
public final class CogReader {
    public static final class Level {
        public int width, height, tileW, tileH, compression = 1, predictor = 1;
        long[] offsets, counts;
        public double pixelDegX, pixelDegY;
    }

    public final String name, url;
    public final List<Level> levels = new ArrayList<>();     // 0 = full resolution
    public double originLon, originLat;                       // top-left corner
    private final RangeCache cache;

    private CogReader(String name, String url, RangeCache cache) {
        this.name = name;
        this.url = url;
        this.cache = cache;
    }

    /** opens the file, or null if it doesn't exist (e.g. open ocean) */
    public static CogReader open(String name, String url, RangeCache cache) throws IOException {
        CogReader r = new CogReader(name, url, cache);
        return r.parse() ? r : null;
    }

    private ByteBuffer range(long off, int len) throws IOException {
        // align small reads into 16 KiB blocks so header/array lookups share cached pieces
        long start = (off / 16384) * 16384;
        int span = (int) (((off + len + 16383) / 16384) * 16384 - start);
        byte[] b = this.cache.get(this.name, this.url, start, span);
        if (b == null) throw new IOException("file vanished: " + this.url);
        ByteBuffer bb = ByteBuffer.wrap(b).order(this.order);
        bb.position((int) (off - start));
        if (bb.remaining() < len) throw new IOException("short read at " + off + " in " + this.url);
        return bb.slice().order(this.order);
    }

    private ByteOrder order = ByteOrder.LITTLE_ENDIAN;
    private boolean big;

    private boolean parse() throws IOException {
        byte[] head = this.cache.get(this.name, this.url, 0, 16384);
        if (head == null) return false;
        this.order = head[0] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer h = ByteBuffer.wrap(head).order(this.order);
        int magic = h.getShort(2) & 0xFFFF;
        this.big = magic == 43;
        long ifd = this.big ? h.getLong(8) : (h.getInt(4) & 0xFFFFFFFFL);
        double[] scale = null, tie = null;
        while (ifd != 0) {
            ByteBuffer cnt = this.range(ifd, this.big ? 8 : 2);
            long n = this.big ? cnt.getLong() : (cnt.getShort() & 0xFFFF);
            int entry = this.big ? 20 : 12;
            ByteBuffer e = this.range(ifd + (this.big ? 8 : 2), (int) (n * entry + (this.big ? 8 : 4)));
            Level lv = new Level();
            long subfile = 0;
            for (int i = 0; i < n; i++) {
                e.position(i * entry);
                int tag = e.getShort() & 0xFFFF, type = e.getShort() & 0xFFFF;
                long count = this.big ? e.getLong() : (e.getInt() & 0xFFFFFFFFL);
                int valuePos = e.position();
                switch (tag) {
                    case 254 -> subfile = this.longs(e, valuePos, type, count)[0];
                    case 256 -> lv.width = (int) this.longs(e, valuePos, type, count)[0];
                    case 257 -> lv.height = (int) this.longs(e, valuePos, type, count)[0];
                    case 259 -> lv.compression = (int) this.longs(e, valuePos, type, count)[0];
                    case 317 -> lv.predictor = (int) this.longs(e, valuePos, type, count)[0];
                    case 322 -> lv.tileW = (int) this.longs(e, valuePos, type, count)[0];
                    case 323 -> lv.tileH = (int) this.longs(e, valuePos, type, count)[0];
                    case 324 -> lv.offsets = this.longs(e, valuePos, type, count);
                    case 325 -> lv.counts = this.longs(e, valuePos, type, count);
                    case 33550 -> scale = this.doubles(e, valuePos, count);
                    case 33922 -> tie = this.doubles(e, valuePos, count);
                    default -> {
                    }
                }
            }
            e.position((int) (n * entry));
            long next = this.big ? e.getLong() : (e.getInt() & 0xFFFFFFFFL);
            if ((subfile & 4) == 0 && lv.offsets != null && lv.tileW > 0) this.levels.add(lv);   // skip mask images
            ifd = next;
        }
        if (this.levels.isEmpty() || scale == null || tie == null) throw new IOException("not a tiled GeoTIFF: " + this.url);
        this.originLon = tie[3] - tie[0] * scale[0];
        this.originLat = tie[4] + tie[1] * scale[1];
        Level l0 = this.levels.get(0);
        for (Level lv : this.levels) {
            lv.pixelDegX = scale[0] * l0.width / (double) lv.width;
            lv.pixelDegY = scale[1] * l0.height / (double) lv.height;
        }
        return true;
    }

    private ByteBuffer valueBuffer(ByteBuffer e, int valuePos, int size) throws IOException {
        int inline = this.big ? 8 : 4;
        if (size <= inline) {
            ByteBuffer v = e.duplicate().order(this.order);
            v.position(valuePos);
            return v.slice().order(this.order);
        }
        long off = this.big ? e.getLong(valuePos) : (e.getInt(valuePos) & 0xFFFFFFFFL);
        return this.range(off, size);
    }

    private long[] longs(ByteBuffer e, int valuePos, int type, long count) throws IOException {
        int sz = type == 3 ? 2 : type == 16 ? 8 : type == 1 || type == 7 ? 1 : 4;
        ByteBuffer v = this.valueBuffer(e, valuePos, (int) (sz * count));
        long[] out = new long[(int) count];
        for (int i = 0; i < count; i++) {
            out[i] = switch (sz) {
                case 1 -> v.get() & 0xFF;
                case 2 -> v.getShort() & 0xFFFF;
                case 8 -> v.getLong();
                default -> v.getInt() & 0xFFFFFFFFL;
            };
        }
        return out;
    }

    private double[] doubles(ByteBuffer e, int valuePos, long count) throws IOException {
        ByteBuffer v = this.valueBuffer(e, valuePos, (int) (8 * count));
        double[] out = new double[(int) count];
        for (int i = 0; i < count; i++) out[i] = v.getDouble();
        return out;
    }

    /** decoded 8-bit tile (tileW * tileH), or null if the tile is empty */
    public byte[] tile(int level, int tx, int ty) throws IOException {
        Level lv = this.levels.get(level);
        int across = (lv.width + lv.tileW - 1) / lv.tileW;
        int idx = tx + ty * across;
        if (idx < 0 || idx >= lv.offsets.length || lv.counts[idx] == 0) return null;
        byte[] raw = this.cache.get(this.name, this.url, lv.offsets[idx], (int) lv.counts[idx]);
        if (raw == null) throw new IOException("file vanished: " + this.url);
        byte[] out = switch (lv.compression) {
            case 1 -> raw;
            case 5 -> lzw(raw, lv.tileW * lv.tileH);
            case 8, 32946 -> inflate(raw, lv.tileW * lv.tileH);
            default -> throw new IOException("unsupported TIFF compression " + lv.compression);
        };
        if (lv.predictor == 2) {
            for (int y = 0; y < lv.tileH; y++) {
                int row = y * lv.tileW;
                for (int x = 1; x < lv.tileW; x++) out[row + x] += out[row + x - 1];
            }
        }
        return out;
    }

    private static byte[] inflate(byte[] in, int size) throws IOException {
        Inflater inf = new Inflater();
        inf.setInput(in);
        byte[] out = new byte[size];
        try {
            int n = 0;
            while (n < size && !inf.finished()) {
                int r = inf.inflate(out, n, size - n);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += r;
            }
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inf.end();
        }
        return out;
    }

    private static byte[] lzw(byte[] in, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        byte[][] table = new byte[4096][];
        for (int i = 0; i < 256; i++) table[i] = new byte[]{(byte) i};
        int next = 258, width = 9;
        long bitBuf = 0;
        int bits = 0, pos = 0;
        byte[] prev = null;
        while (true) {
            while (bits < width && pos < in.length) {
                bitBuf = (bitBuf << 8) | (in[pos++] & 0xFF);
                bits += 8;
            }
            if (bits < width) break;
            int code = (int) ((bitBuf >> (bits - width)) & ((1 << width) - 1));
            bits -= width;
            if (code == 257) break;
            if (code == 256) {
                next = 258;
                width = 9;
                prev = null;
                continue;
            }
            byte[] entry;
            if (code < next && table[code] != null) entry = table[code];
            else if (prev != null) {
                entry = java.util.Arrays.copyOf(prev, prev.length + 1);
                entry[prev.length] = prev[0];
            } else break;
            out.write(entry, 0, entry.length);
            if (prev != null && next < 4096) {
                byte[] add = java.util.Arrays.copyOf(prev, prev.length + 1);
                add[prev.length] = entry[0];
                table[next++] = add;
            }
            prev = entry;
            if (next + 1 >= (1 << width) && width < 12) width++;
        }
        byte[] r = out.toByteArray();
        return r.length >= size ? r : java.util.Arrays.copyOf(r, size);
    }
}
