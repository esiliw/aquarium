package dev.esiliw.aquarium.data;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

/** Network diagnostics against the real Terrarium data server. Only runs with AQUARIUM_DIAG=1 (in CI). */
public class DiagTest {
    static void d(String fmt, Object... a) {
        System.out.println("DIAG " + String.format(Locale.ROOT, fmt, a));
    }

    @Test
    void diagnostics() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AQUARIUM_DIAG")));
        HttpClient http = HttpClient.newHttpClient();
        for (String path : new String[]{"ocean/7/54", "ocean/8/54", "ocean/0/0", "ocean/-30/30", "elevation2/4/0/0"}) {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(GeoTileSource.ENDPOINT + "/" + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] b = r.body();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, b.length); i++) hex.append(String.format("%02x", b[i]));
            d("GET %s -> %d, %d bytes, head %s, content-type %s", path, r.statusCode(), b.length, hex, r.headers().firstValue("content-type").orElse("?"));
        }

        OceanPolygons ocean = new OceanPolygons();
        double[][] pts = {{54.1825, 7.8856, 0}, {54.1870, 7.8700, 0}, {54.15, 7.95, 1}, {54.25, 7.70, 1}, {53.55, 8.58, 0}, {53.9, 8.2, 1}, {54.18, 7.91, 0}};
        for (double[] p : pts) d("ocean %.4f,%.4f expected %s -> %s", p[0], p[1], p[2] == 1 ? "sea" : "land", ocean.oceanAt(p[0], p[1]));

        EarthData data = EarthData.forScale(10);
        d("zooms: elevation %d, cover %d, cover px %.1f m", data.elevationZoom(), data.coverZoom(), data.coverPixelMeters());
        String[][] places = {{"Helgoland", "54.1825", "7.8856"}, {"Helgoland sea", "54.15", "7.95"},
                {"Hufeisensee", "51.4840", "12.0300"}, {"Hufeisensee shore", "51.4800", "12.0150"},
                {"Geiseltalsee", "51.2900", "11.8800"}, {"Geiseltalsee 2", "51.3050", "11.9300"}, {"user lake 1:10", "51.2830", "11.9140"}, {"Hufeisensee 2", "51.4975", "12.0420"}, {"Geiseltal rim", "51.3300", "11.9000"},
                {"Halle city", "51.4820", "11.9700"}, {"Lake Constance", "47.6000", "9.4000"}, {"Mueritz", "53.4300", "12.6800"}};
        for (String[] p : places) {
            double lat = Double.parseDouble(p[1]), lon = Double.parseDouble(p[2]);
            EarthData.Water w = data.water(lat, lon);
            d("%-18s elev %.1f m  cover %s  ocean %s  water frac %.2f level %.1f shoreDist %.1f px", p[0], data.elevation(lat, lon),
                    data.cover(lat, lon), data.oceanAt(lat, lon), w.fraction(), w.level(), w.shoreDistance());
        }
        // elevation profile across Hufeisensee (west-east)
        StringBuilder sb = new StringBuilder();
        for (double lon = 11.99; lon <= 12.07; lon += 0.004) sb.append(String.format(Locale.ROOT, "%.0f ", data.elevation(51.4840, lon)));
        d("profile Hufeisensee W-E: %s", sb);
        sb = new StringBuilder();
        for (double lon = 11.80; lon <= 12.00; lon += 0.01) sb.append(String.format(Locale.ROOT, "%.0f ", data.elevation(51.2950, lon)));
        d("profile Geiseltalsee W-E: %s", sb);
        EarthData d40 = EarthData.forScale(40);
        sb = new StringBuilder();
        for (double lon = -157.2; lon <= -155.9; lon += 0.05) sb.append(String.format(Locale.ROOT, "%.0f/%.0f ", d40.elevation(19.6, lon), d40.bathymetry(19.6, lon)));
        d("profile off Hawaii W-E (fine/filled): %s", sb);
        for (double mpp : new double[]{10, 40}) {
            WorldCover wc = new WorldCover(mpp);
            String[][] wcPts = {{"Halle centre (urban)", "51.4820", "11.9700"}, {"Hufeisensee (water)", "51.4840", "12.0300"},
                    {"Geiseltalsee (water)", "51.2950", "11.8800"}, {"Harz forest", "51.7500", "10.6000"}, {"Sahara (bare)", "25.0", "10.0"},
                    {"Amazon (tree)", "-3.0", "-60.0"}, {"Matterhorn (snow)", "45.9763", "7.6586"}, {"open Atlantic", "40.0", "-40.0"}};
            for (String[] p : wcPts) d("WorldCover %.0f m: %-22s class %d -> %s", mpp, p[0], wc.classAt(Double.parseDouble(p[1]), Double.parseDouble(p[2])),
                    wc.cover(Double.parseDouble(p[1]), Double.parseDouble(p[2])));
        }
        for (int z : new int[]{10, 13, 15}) {
            AwsElevation aws = new AwsElevation(z, 64);
            sb = new StringBuilder();
            for (double lon = -157.2; lon <= -155.9; lon += 0.05) sb.append(String.format(Locale.ROOT, "%.0f ", aws.elevation(19.6, lon)));
            d("AWS z%d off Hawaii W-E: %s", z, sb);
            sb = new StringBuilder();
            for (double lon = 7.80; lon <= 7.98; lon += 0.01) sb.append(String.format(Locale.ROOT, "%.0f ", aws.elevation(54.183, lon)));
            d("AWS z%d Helgoland W-E: %s", z, sb);
            sb = new StringBuilder();
            for (double lon = 12.20; lon <= 12.50; lon += 0.02) sb.append(String.format(Locale.ROOT, "%.1f ", aws.elevation(45.43, lon)));
            d("AWS z%d Venice lagoon W-E: %s", z, sb);
            d("AWS z%d Lake Constance %.1f, Dead Sea %.1f, Everest %.0f, Geiseltalsee %.1f", z, aws.elevation(47.6, 9.4), aws.elevation(31.5, 35.5),
                    aws.elevation(27.9881, 86.9250), aws.elevation(51.295, 11.88));
        }
    }
}
