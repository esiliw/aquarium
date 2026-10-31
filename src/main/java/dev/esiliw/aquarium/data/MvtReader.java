/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal Mapbox Vector Tile (protobuf) decoder: layers, features, tags and geometry in tile coordinates. */
public final class MvtReader {
    public static final int POINT = 1, LINE = 2, POLYGON = 3;

    public record Feature(int type, Map<String, Object> tags, List<int[]> rings) {
        /** rings/lines as flat x,y arrays in tile units (0..extent) */
        public String str(String key) {
            Object o = this.tags.get(key);
            return o == null ? null : o.toString();
        }
    }

    public record Layer(String name, int extent, List<Feature> features) {
    }

    public static Map<String, Layer> read(byte[] data) throws IOException {
        if (data.length > 2 && (data[0] & 0xFF) == 0x1f && (data[1] & 0xFF) == 0x8b) {
            data = new GZIPInputStream(new ByteArrayInputStream(data)).readAllBytes();
        }
        Map<String, Layer> out = new HashMap<>();
        Pb pb = new Pb(data, 0, data.length);
        while (pb.more()) {
            int key = (int) pb.varint();
            if ((key >> 3) == 3 && (key & 7) == 2) {
                Pb sub = pb.sub();
                Layer l = layer(sub);
                out.put(l.name, l);
            } else pb.skip(key & 7);
        }
        return out;
    }

    private static Layer layer(Pb pb) {
        String name = "";
        int extent = 4096;
        List<Pb> feats = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        while (pb.more()) {
            int key = (int) pb.varint();
            int field = key >> 3;
            if (field == 1 && (key & 7) == 2) name = pb.string();
            else if (field == 2 && (key & 7) == 2) feats.add(pb.sub());
            else if (field == 3 && (key & 7) == 2) keys.add(pb.string());
            else if (field == 4 && (key & 7) == 2) values.add(value(pb.sub()));
            else if (field == 5 && (key & 7) == 0) extent = (int) pb.varint();
            else pb.skip(key & 7);
        }
        List<Feature> features = new ArrayList<>(feats.size());
        for (Pb f : feats) features.add(feature(f, keys, values));
        return new Layer(name, extent, features);
    }

    private static Object value(Pb pb) {
        Object v = null;
        while (pb.more()) {
            int key = (int) pb.varint();
            switch (key >> 3) {
                case 1 -> v = pb.string();
                case 2 -> v = Float.intBitsToFloat(pb.fixed32());
                case 3 -> v = Double.longBitsToDouble(pb.fixed64());
                case 4, 5 -> v = pb.varint();
                case 6 -> {
                    long z = pb.varint();
                    v = (z >>> 1) ^ -(z & 1);
                }
                case 7 -> v = pb.varint() != 0;
                default -> pb.skip(key & 7);
            }
        }
        return v;
    }

    private static Feature feature(Pb pb, List<String> keys, List<Object> values) {
        int type = 0;
        Map<String, Object> tags = new HashMap<>();
        int[] geom = new int[0];
        while (pb.more()) {
            int key = (int) pb.varint();
            int field = key >> 3;
            if (field == 2 && (key & 7) == 2) {
                Pb p = pb.sub();
                while (p.more()) {
                    int k = (int) p.varint(), v = (int) p.varint();
                    if (k < keys.size() && v < values.size()) tags.put(keys.get(k), values.get(v));
                }
            } else if (field == 3 && (key & 7) == 0) type = (int) pb.varint();
            else if (field == 4 && (key & 7) == 2) {
                Pb p = pb.sub();
                int[] tmp = new int[64];
                int n = 0;
                while (p.more()) {
                    if (n == tmp.length) tmp = java.util.Arrays.copyOf(tmp, n * 2);
                    tmp[n++] = (int) p.varint();
                }
                geom = java.util.Arrays.copyOf(tmp, n);
            } else pb.skip(key & 7);
        }
        return new Feature(type, tags, decodeGeometry(geom));
    }

    private static List<int[]> decodeGeometry(int[] g) {
        List<int[]> parts = new ArrayList<>();
        int x = 0, y = 0, i = 0;
        int[] cur = null;
        int n = 0;
        while (i < g.length) {
            int cmd = g[i] & 7, count = g[i] >>> 3;
            i++;
            if (cmd == 1 || cmd == 2) {
                for (int c = 0; c < count && i + 1 < g.length; c++) {
                    x += (g[i] >>> 1) ^ -(g[i] & 1);
                    y += (g[i + 1] >>> 1) ^ -(g[i + 1] & 1);
                    i += 2;
                    if (cmd == 1) {
                        if (cur != null && n >= 2) parts.add(java.util.Arrays.copyOf(cur, n));
                        cur = new int[16];
                        n = 0;
                    }
                    if (cur == null) {
                        cur = new int[16];
                        n = 0;
                    }
                    if (n + 2 > cur.length) cur = java.util.Arrays.copyOf(cur, cur.length * 2);
                    cur[n++] = x;
                    cur[n++] = y;
                }
            } else if (cmd == 7) {
                if (cur != null && n >= 2) parts.add(java.util.Arrays.copyOf(cur, n));
                cur = null;
                n = 0;
            } else break;
        }
        if (cur != null && n >= 2) parts.add(java.util.Arrays.copyOf(cur, n));
        return parts;
    }

    /** tiny protobuf cursor */
    private static final class Pb {
        final byte[] b;
        int pos;
        final int end;

        Pb(byte[] b, int pos, int end) {
            this.b = b;
            this.pos = pos;
            this.end = end;
        }

        boolean more() {
            return this.pos < this.end;
        }

        long varint() {
            long r = 0;
            int shift = 0;
            while (this.pos < this.end) {
                int v = this.b[this.pos++] & 0xFF;
                r |= (long) (v & 0x7F) << shift;
                if (v < 0x80) break;
                shift += 7;
            }
            return r;
        }

        Pb sub() {
            int len = (int) this.varint();
            Pb p = new Pb(this.b, this.pos, Math.min(this.end, this.pos + len));
            this.pos += len;
            return p;
        }

        String string() {
            int len = (int) this.varint();
            String s = new String(this.b, this.pos, len, java.nio.charset.StandardCharsets.UTF_8);
            this.pos += len;
            return s;
        }

        int fixed32() {
            int v = (this.b[this.pos] & 0xFF) | (this.b[this.pos + 1] & 0xFF) << 8 | (this.b[this.pos + 2] & 0xFF) << 16 | (this.b[this.pos + 3] & 0xFF) << 24;
            this.pos += 4;
            return v;
        }

        long fixed64() {
            long lo = this.fixed32() & 0xFFFFFFFFL, hi = this.fixed32() & 0xFFFFFFFFL;
            return lo | hi << 32;
        }

        void skip(int wire) {
            switch (wire) {
                case 0 -> this.varint();
                case 1 -> this.pos += 8;
                case 2 -> this.pos += (int) this.varint();
                case 5 -> this.pos += 4;
                default -> this.pos = this.end;
            }
        }
    }
}
