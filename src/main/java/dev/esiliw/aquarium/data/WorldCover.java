/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import dev.esiliw.aquarium.world.Cover;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ESA WorldCover 2021 v200, 10 m land cover (CC BY 4.0), from the public AWS bucket.
 * 3x3 degree cloud-optimised GeoTIFFs; only the tiles that are needed get downloaded.
 */
public final class WorldCover {
    private static final String BASE = "https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map/";
    private static final Object NONE = new Object();
    private final RangeCache cache = new RangeCache("worldcover");
    private final Map<String, Object> files = new ConcurrentHashMap<>();
    private final int level;
    private final Map<String, byte[]> tiles = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return this.size() > 48;
        }
    };

    /** @param metersPerPixel the coarsest pixel size that is still fine enough (picks an overview level) */
    public WorldCover(double metersPerPixel) {
        this.level = Math.max(0, (int) Math.floor(Math.log(Math.max(1, metersPerPixel / 10.0)) / Math.log(2)));
    }

    static String fileName(int lat0, int lon0) {
        return String.format(Locale.ROOT, "ESA_WorldCover_10m_2021_v200_%s%02d%s%03d_Map.tif",
                lat0 < 0 ? "S" : "N", Math.abs(lat0), lon0 < 0 ? "W" : "E", Math.abs(lon0));
    }

    private CogReader file(int lat0, int lon0) {
        String name = fileName(lat0, lon0);
        Object o = this.files.computeIfAbsent(name, n -> {
            try {
                CogReader r = CogReader.open(n, BASE + n, this.cache);
                return r == null ? NONE : r;
            } catch (IOException e) {
                DataEnv.LOGGER.error("WorldCover file {} unreadable: {}", n, e.toString());
                return NONE;
            }
        });
        return o == NONE ? null : (CogReader) o;
    }

    /** raw WorldCover class (10, 20, ... 100) or 0 where there is no data (open sea) */
    public int classAt(double lat, double lon) {
        int lat0 = Math.floorDiv((int) Math.floor(lat), 3) * 3, lon0 = Math.floorDiv((int) Math.floor(lon), 3) * 3;
        CogReader f = this.file(lat0, lon0);
        if (f == null) return 0;
        int lvIdx = Math.min(this.level, f.levels.size() - 1);
        CogReader.Level lv = f.levels.get(lvIdx);
        int px = (int) Math.floor((lon - f.originLon) / lv.pixelDegX), py = (int) Math.floor((f.originLat - lat) / lv.pixelDegY);
        px = Math.max(0, Math.min(lv.width - 1, px));
        py = Math.max(0, Math.min(lv.height - 1, py));
        int tx = px / lv.tileW, ty = py / lv.tileH;
        String key = f.name + "/" + lvIdx + "/" + tx + "/" + ty;
        byte[] t;
        synchronized (this.tiles) {
            t = this.tiles.get(key);
        }
        if (t == null) {
            try {
                t = f.tile(lvIdx, tx, ty);
            } catch (IOException e) {
                DataEnv.LOGGER.error("WorldCover tile {} unreadable: {}", key, e.toString());
            }
            if (t == null) t = new byte[0];
            synchronized (this.tiles) {
                this.tiles.put(key, t);
            }
        }
        if (t.length == 0) return 0;
        return t[(px % lv.tileW) + (py % lv.tileH) * lv.tileW] & 0xFF;
    }

    /** WorldCover class translated to the ESA CCI classes the rest of the generator understands */
    public Cover cover(double lat, double lon) {
        int c = this.classAt(lat, lon);
        return switch (c) {
            case 10 -> Math.abs(lat) < 23 ? Cover.BROADLEAF_EVERGREEN : Math.abs(lat) > 52 ? Cover.NEEDLEAF_EVERGREEN : Cover.MIXED_LEAF_TYPE;
            case 20 -> Cover.SHRUBLAND;
            case 30 -> Cover.GRASSLAND;
            case 40 -> Cover.RAINFED_CROPLAND;
            case 50 -> Cover.URBAN;
            case 60 -> Cover.BARE;
            case 70 -> Cover.PERMANENT_SNOW;
            case 80 -> Cover.WATER;
            case 90 -> Cover.FLOODED_VEGETATION;
            case 95 -> Cover.SALINE_FLOODED_FOREST;
            case 100 -> Cover.LICHENS_AND_MOSSES;
            default -> Cover.NO;
        };
    }
}
