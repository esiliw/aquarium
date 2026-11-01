package dev.esiliw.aquarium.data;

import dev.esiliw.aquarium.world.EarthSettings;
import dev.esiliw.aquarium.world.TerrainModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/** Renders top-down maps of the generated terrain for a few places (CI only, AQUARIUM_DIAG=1). */
public class RenderDiagTest {
    record Place(String name, double scale, double vscale, double lat0, double lon0, double lat1, double lon1, int step) {
    }

    @Test
    void render() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AQUARIUM_DIAG")));
        System.setProperty("java.awt.headless", "true");
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();   // biome keys need the registries
        File out = new File("build/diag");
        out.mkdirs();
        Place[] places = {
                new Place("helgoland", 4, 5, 54.170, 7.865, 54.195, 7.925, 1),
                new Place("geiseltal", 4, 5, 51.255, 11.82, 51.325, 12.00, 2),
                new Place("niigata", 4, 5, 37.85, 138.95, 38.00, 139.20, 4),          // coast, river mouth, city
                new Place("malmo", 10, 10, 55.57, 12.95, 55.63, 13.06, 1),            // default start: streets, rail, stations
                new Place("california", 40, 40, 34.6, -121.6, 36.6, -119.4, 2),       // Mediterranean: woodland, chaparral, golden hills
                new Place("spain", 100, 100, 36.5, -7.5, 42.5, 0.5, 2),               // Mediterranean + steppe interior
                new Place("germany", 100, 100, 47.5, 6.0, 54.5, 14.5, 2),             // mixed forest, plains, uplands
                new Place("japan", 100, 100, 33.0, 132.0, 39.0, 141.0, 2),            // humid subtropical vs mountains
                new Place("central_asia", 400, 400, 36.0, 50.0, 50.0, 80.0, 2),       // cold desert, steppe
                new Place("scandinavia", 400, 400, 55.0, 4.0, 71.0, 31.0, 2),         // taiga, tundra, mixed forest
                new Place("australia", 1000, 1000, -39.0, 113.0, -11.0, 154.0, 2),    // red Outback, savanna, temperate south-east
                new Place("iceland", 100, 100, 63.3, -24.5, 66.6, -13.5, 2),          // volcanic: black lava, moss, tundra
                new Place("halle_rail", 4, 5, 51.465, 11.96, 51.49, 12.00, 1),        // rail hub, trams
                new Place("geirangerfjord", 4, 5, 62.06, 6.95, 62.16, 7.25, 4),       // fjord, steep mountains
                new Place("venice_lagoon", 4, 5, 45.35, 12.20, 45.55, 12.50, 4),      // lagoon, islands
                new Place("nile_delta", 40, 40, 30.0, 30.0, 31.6, 32.4, 4),           // desert, delta, farmland
                new Place("alps_matterhorn", 4, 5, 45.95, 7.60, 46.05, 7.75, 4),      // high mountains, glaciers
                new Place("finland_lakes", 40, 40, 61.0, 26.0, 62.5, 29.0, 4),        // thousands of lakes
                new Place("hawaii_big_island", 40, 40, 18.9, -156.1, 20.3, -154.8, 2), // volcano, steep coast
                new Place("rio_de_janeiro", 4, 5, -23.00, -43.30, -22.85, -43.10, 4),  // bay, steep hills, southern hemisphere
        };
        Throwable first = null;
        for (Place p : places) {
            try {
                renderPlace(p, out);
            } catch (Throwable t) {
                if (first == null) first = t;
                for (Throwable c = t; c != null; c = c.getCause()) {
                    System.out.println("DIAG ERROR " + p.name + ": " + c);
                    StackTraceElement[] st = c.getStackTrace();
                    for (int k = 0; k < Math.min(12, st.length); k++) System.out.println("DIAG   at " + st[k]);
                }
            }
        }
        if (first != null) throw new AssertionError(first);
    }

    static void renderPlace(Place p, File out) throws Exception {
        EarthSettings s = new EarthSettings(p.scale, p.vscale, 0, 63, -512, 2544, 0, 0, GeoProjection.Type.MERCATOR, EarthData.Source.OPEN, OsmWater.DEFAULT_SNAPSHOT, true, true);
        GeoProjection proj = s.projection();
        TerrainModel model = new TerrainModel(s, proj, s.createData());
        EarthData data = model.data();
        int bx0 = (int) proj.blockX(p.lon0), bx1 = (int) proj.blockX(p.lon1);
        int bz0 = (int) proj.blockZ(p.lat1), bz1 = (int) proj.blockZ(p.lat0);
        int step = Math.max(p.step, (int) Math.ceil(Math.max(bx1 - bx0, bz1 - bz0) / 1600.0));   // keep renders within the heap
        p = new Place(p.name, p.scale, p.vscale, p.lat0, p.lon0, p.lat1, p.lon1, step);
        int w = (bx1 - bx0) / p.step, h = (bz1 - bz0) / p.step;
        BufferedImage fin = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage raw = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] tops = new int[w * h];
        TerrainModel.Column[] cols = new TerrainModel.Column[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = bx0 + i * p.step, z = bz0 + j * p.step;
                cols[i + j * w] = model.column(x, z);
                tops[i + j * w] = cols[i + j * w].top();
            }
        }
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                TerrainModel.Column c = cols[i + j * w];
                int rgb;
                if (c.waterTop() != Integer.MIN_VALUE && c.top() < c.waterTop()) {
                    int depth = c.waterTop() - c.top();
                    double t = Math.min(1, depth / 12.0);
                    if (c.ocean()) rgb = mix(0x8ec8ff, 0x0a2a8a, t);
                    else rgb = mix(0x9ff0e0, 0x0a6a5a, t);
                    if (c.waterTop() != 62) rgb = mix(rgb, 0xff00ff, 0.35);   // water surface not at sea level
                } else if (c.buildingTop() > c.top()) {
                    int bh = c.buildingTop() - c.top();
                    rgb = mix(0xc8c0b8, 0x504840, Math.min(1, bh / 20.0));      // buildings: darker = taller
                } else if (c.way() != OsmWays.NONE) {
                    rgb = switch (c.way()) {
                        case OsmWays.MAJOR -> 0x202020;
                        case OsmWays.SECONDARY -> 0x606060;
                        case OsmWays.MINOR -> 0xb08850;
                        case OsmWays.RAIL_BED -> 0x8a8a8a;
                        case OsmWays.TRAM -> 0xe0c020;
                        case OsmWays.RAIL_LINE -> 0xd0d0e0;
                        default -> 0xe03030;
                    };
                } else {
                    // banded colours so every block of height is visible, whatever the absolute elevation
                    int band = Math.floorMod(c.top(), 10);
                    rgb = mix(0x9ccc65, 0x6d4c41, band / 10.0);
                    if (c.top() <= 63) rgb = 0xf0e68c;                      // at sea level
                    if (model.surfaceY(c.elevation()) < c.top()) rgb = 0xff8c00;   // ground raised to hold a lake in
                    int west = i > 0 ? tops[i - 1 + j * w] : c.top(), north = j > 0 ? tops[i + (j - 1) * w] : c.top();
                    int shade = (c.top() - west) + (c.top() - north);
                    rgb = shade > 0 ? mix(rgb, 0xffffff, Math.min(0.5, shade * 0.15)) : shade < 0 ? mix(rgb, 0x000000, Math.min(0.5, -shade * 0.15)) : rgb;
                }
                fin.setRGB(i, j, rgb);

                double lat = proj.lat(bz0 + j * p.step + 0.5), lon = proj.lon(bx0 + i * p.step + 0.5);
                double e = data.elevation(lat, lon);
                Boolean oc = data.oceanAt(lat, lon);
                EarthData.Water wt = data.water(lat, lon);
                int g = (int) Math.max(0, Math.min(255, 60 + Math.floorMod((int) e, 40) * 4));
                int r2 = g, g2 = g, b2 = g;
                if (e < 0) { r2 = 40; g2 = 40; b2 = (int) Math.max(80, 200 + e * 3); }
                if (oc == null) r2 = Math.min(255, r2 + 90);                 // red tint: no outline data
                else if (oc) b2 = Math.min(255, b2 + 90);                   // blue tint: outline says sea
                if (wt.fraction() > 0) { r2 /= 3; g2 = Math.min(255, 120 + (int) (130 * wt.fraction())); b2 /= 3; }   // green: land-cover water
                raw.setRGB(i, j, (r2 << 16) | (g2 << 8) | b2);
            }
        }
        ImageIO.write(fin, "png", new File(out, p.name + "_final.png"));
        BufferedImage bio = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.util.Map<String, Integer> legend = new java.util.TreeMap<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                TerrainModel.Column c = cols[i + j * w];
                double lat = proj.lat(bz0 + j * p.step + 0.5), lon = proj.lon(bx0 + i * p.step + 0.5);
                String b = dev.esiliw.aquarium.world.EarthBiomes.select(c.cover(), c.elevation(), lat, lon, c.ocean(), Climate.at(lat, lon)).location().getPath();
                int rgb = c.lake() ? 0x4080ff : biomeColor(b);
                bio.setRGB(i, j, rgb);
                legend.merge(b, 1, Integer::sum);
            }
        }
        ImageIO.write(bio, "png", new File(out, p.name + "_biomes.png"));
        System.out.println("DIAG biomes " + p.name + ": " + legend);
        ImageIO.write(raw, "png", new File(out, p.name + "_data.png"));
        System.out.println("DIAG " + String.format(Locale.ROOT, "rendered %s %dx%d (step %d) blocks x %d..%d z %d..%d", p.name, w, h, p.step, bx0, bx1, bz0, bz1));
    }

    static int biomeColor(String b) {
        return switch (b) {
            case "plains" -> 0x8db360; case "meadow" -> 0xa8d878; case "forest" -> 0x2f7a2f; case "mixed_forest" -> 0x3a6e4a;
            case "birch_forest" -> 0x5aa05a; case "taiga" -> 0x2f5f4f; case "snowy_taiga" -> 0x9fb8b0; case "old_growth_spruce_taiga" -> 0x1f4f3f;
            case "subtropical_forest" -> 0x1f9f5f; case "jungle", "sparse_jungle" -> 0x0f7f2f; case "savanna" -> 0xbdb25f;
            case "mediterranean_woodland" -> 0x8f9f3f; case "chaparral" -> 0xa08f4f; case "steppe" -> 0xd8c878; case "desert" -> 0xf0e0a0;
            case "cold_desert" -> 0xb8a890; case "badlands" -> 0xc06030; case "outback" -> 0xd08850; case "tundra" -> 0x8a9a7a; case "snowy_plains" -> 0xf0f0f0;
            case "swamp", "mangrove_swamp" -> 0x4f6f3f; case "stony_peaks", "windswept_hills" -> 0x8a8a8a;
            case "frozen_peaks", "jagged_peaks", "snowy_slopes", "ice_spikes" -> 0xe0e8f0; case "river" -> 0x4080ff;
            default -> b.contains("ocean") ? 0x203a80 : 0xff00ff;
        };
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
