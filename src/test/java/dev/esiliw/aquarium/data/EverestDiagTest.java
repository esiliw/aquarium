package dev.esiliw.aquarium.data;

import dev.esiliw.aquarium.world.EarthSettings;
import dev.esiliw.aquarium.world.TerrainModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Looks for spikes and walls around Mount Everest at 1:10 (CI only, AQUARIUM_DIAG=1). */
public class EverestDiagTest {
    @Test
    void everest() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AQUARIUM_DIAG")));
        EarthSettings s = new EarthSettings(10, 10, 0, 63, -512, 2544, 0, 0, GeoProjection.Type.MERCATOR, EarthData.Source.OPEN, OsmWater.DEFAULT_SNAPSHOT, true, true);
        GeoProjection proj = s.projection();
        TerrainModel model = new TerrainModel(s, proj, s.createData());
        int bx0 = (int) proj.blockX(86.86), bx1 = (int) proj.blockX(86.99);
        int bz0 = (int) proj.blockZ(28.04), bz1 = (int) proj.blockZ(27.94);
        int w = bx1 - bx0, h = bz1 - bz0;
        TerrainModel.Column[] cols = new TerrainModel.Column[w * h];
        int maxTop = Integer.MIN_VALUE, maxHigh = Integer.MIN_VALUE;
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            TerrainModel.Column c = model.column(bx0 + i, bz0 + j);
            cols[i + j * w] = c;
            maxTop = Math.max(maxTop, c.top());
            maxHigh = Math.max(maxHigh, c.highest());
        }
        System.out.println("DIAG everest " + w + "x" + h + " maxTop " + maxTop + " maxHighest " + maxHigh);
        int zoom = AwsElevation.zoomFor(10);
        AwsElevation aws = new AwsElevation(zoom, 64);
        int printed = 0, spikes = 0, buildings = 0, ways = 0, water = 0;
        java.util.Map<String, Integer> wayCount = new java.util.TreeMap<>();
        for (int j = 2; j < h - 2; j++) for (int i = 2; i < w - 2; i++) {
            TerrainModel.Column c = cols[i + j * w];
            if (c.buildingTop() > c.top()) buildings++;
            if (c.way() != OsmWays.NONE) { ways++; wayCount.merge("way" + c.way(), 1, Integer::sum); }
            if (c.lake()) water++;
            int[] nb = new int[24];
            int k = 0;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) if (dx != 0 || dy != 0) nb[k++] = cols[i + dx + (j + dy) * w].highest();
            java.util.Arrays.sort(nb);
            int med = nb[12];
            if (c.highest() - med > 25) {
                spikes++;
                if (printed++ < 40) {
                    double lat = proj.lat(bz0 + j + 0.5), lon = proj.lon(bx0 + i + 0.5);
                    double px = AwsElevation.pixelX(lon, zoom), py = AwsElevation.pixelY(lat, zoom);
                    int ipx = (int) Math.floor(px), ipy = (int) Math.floor(py);
                    StringBuilder raw = new StringBuilder();
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) raw.append((int) aws.raw(ipx + dx, ipy + dy)).append(dy == 1 && dx == 1 ? "" : ",");
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "DIAG everest spike %.5f,%.5f block %d,%d top %d highest %d median %d e %.1f way %d bTop %d lake %b water %d | z%d px %d,%d (in tile %d,%d) raw3x3 %s",
                            lat, lon, bx0 + i, bz0 + j, c.top(), c.highest(), med, c.elevation(), c.way(), c.buildingTop(), c.lake(), c.waterTop(),
                            zoom, ipx, ipy, ipx % 256, ipy % 256, raw));
                }
            }
        }
        System.out.println("DIAG everest spikes " + spikes + " building columns " + buildings + " way columns " + ways + " " + wayCount + " water columns " + water);
    }
}
