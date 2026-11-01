/*
 * Aquarium
 */
package dev.esiliw.aquarium.data;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Köppen-Geiger climate classes 1991-2020 at 0.1 degrees, bundled in the jar (no download needed).
 * Data: Beck, H. E. et al. (2023), High-resolution (1 km) Köppen-Geiger maps for 1901-2099 based on constrained
 * CMIP6 projections, Scientific Data 10, 724. CC BY 4.0. Downsampled for the mod.
 */
public final class Climate {
    public static final byte NONE = 0, Af = 1, Am = 2, Aw = 3, BWh = 4, BWk = 5, BSh = 6, BSk = 7, Csa = 8, Csb = 9, Csc = 10,
            Cwa = 11, Cwb = 12, Cwc = 13, Cfa = 14, Cfb = 15, Cfc = 16, Dsa = 17, Dsb = 18, Dsc = 19, Dsd = 20, Dwa = 21, Dwb = 22,
            Dwc = 23, Dwd = 24, Dfa = 25, Dfb = 26, Dfc = 27, Dfd = 28, ET = 29, EF = 30;
    private static final int W = 3600, H = 1800;
    private static final byte[] MAP = load();

    private Climate() {
    }

    private static byte[] load() {
        try (InputStream in = Climate.class.getResourceAsStream("/aquarium/koppen.png")) {
            BufferedImage img = ImageIO.read(in);
            byte[] out = new byte[W * H];
            java.awt.image.Raster r = img.getRaster();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out[x + y * W] = (byte) r.getSample(x, y, 0);
            return out;
        } catch (Exception e) {
            DataEnv.LOGGER.error("Could not load the bundled climate map, climate-based biomes are off", e);
            return new byte[W * H];
        }
    }

    /** climate class at a point (borders roughened so they aren't squares); near coasts the nearest land class is used */
    public static byte at(double lat, double lon) {
        double fx = (lon + 180.0) * 10.0, fy = (90.0 - lat) * 10.0;
        fx += 0.6 * Noise.smooth(fx * 2.3, fy * 2.3, 41) + 0.25 * Noise.smooth(fx * 7.1, fy * 7.1, 42);
        fy += 0.6 * Noise.smooth(fx * 2.3, fy * 2.3, 43) + 0.25 * Noise.smooth(fx * 7.1, fy * 7.1, 44);
        int x = Math.floorMod((int) Math.floor(fx), W), y = Math.max(0, Math.min(H - 1, (int) Math.floor(fy)));
        byte c = MAP[x + y * W];
        if (c != NONE) return c;
        // coasts and small islands: look outwards for the nearest classified pixel
        for (int r = 1; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    byte v = MAP[Math.floorMod(x + dx, W) + Math.max(0, Math.min(H - 1, y + dy)) * W];
                    if (v != NONE) return v;
                }
            }
        }
        return NONE;
    }

    public static boolean tropical(byte c) {
        return c >= Af && c <= Aw;
    }

    public static boolean hotDesert(byte c) {
        return c == BWh;
    }

    public static boolean coldDesert(byte c) {
        return c == BWk;
    }

    public static boolean steppe(byte c) {
        return c == BSh || c == BSk;
    }

    public static boolean arid(byte c) {
        return c >= BWh && c <= BSk;
    }

    public static boolean mediterranean(byte c) {
        return c >= Csa && c <= Csc;
    }

    public static boolean humidSubtropical(byte c) {
        return c == Cfa || c == Cwa;
    }

    public static boolean temperate(byte c) {
        return c >= Csa && c <= Cfc;
    }

    public static boolean continental(byte c) {
        return c >= Dsa && c <= Dfd;
    }

    /** subarctic: cold summers or very cold winters */
    public static boolean boreal(byte c) {
        return c == Dsc || c == Dsd || c == Dwc || c == Dwd || c == Dfc || c == Dfd;
    }

    public static boolean veryColdWinter(byte c) {
        return c == Dsd || c == Dwd || c == Dfd;
    }
}
