package dev.esiliw.aquarium.data;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Checks which replacement data sources are reachable and what they return (CI only). */
public class SourceProbeTest {
    static void d(String fmt, Object... a) {
        System.out.println("DIAG PROBE " + String.format(Locale.ROOT, fmt, a));
    }

    @Test
    void probe() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AQUARIUM_DIAG")));
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        String[] urls = {
                // elevation (Mapzen/Tilezen terrain tiles on AWS open data)
                "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/10/546/340.png",
                "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/14/8736/5451.png",
                "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/15/17473/10903.png",
                "https://elevation-tiles-prod.s3.amazonaws.com/terrarium/0/0/0.png",
                // ESA WorldCover 10 m (COG GeoTIFF, 3x3 degree tiles)
                "https://esa-worldcover.s3.eu-central-1.amazonaws.com/v200/2021/map/ESA_WorldCover_10m_2021_v200_N51E009_Map.tif",
                "https://esa-worldcover.s3.eu-central-1.amazonaws.com/v100/2020/map/ESA_WorldCover_10m_2020_v100_N51E009_Map.tif",
                // OSM vector tiles
                "https://tiles.openfreemap.org/planet",
                "https://tiles.versatiles.org/tiles/osm/14/8736/5451",
                "https://tiles.versatiles.org/tiles/osm/tiles.json",
                "https://download.versatiles.org/",
        };
        for (String url : urls) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Aquarium-probe/0.3 (Minecraft mod)").header("Range", "bytes=0-65535");
                HttpResponse<byte[]> r = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = r.body();
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(16, body.length); i++) hex.append(String.format("%02x", body[i]));
                String text = new String(body, 0, Math.min(body.length, 600), java.nio.charset.StandardCharsets.ISO_8859_1)
                        .replaceAll("[^\\x20-\\x7e]", ".");
                d("%s -> %d, %d bytes, type %s, range %s, encoding %s, head %s", url, r.statusCode(), body.length,
                        r.headers().firstValue("content-type").orElse("?"), r.headers().firstValue("content-range").orElse("-"),
                        r.headers().firstValue("content-encoding").orElse("-"), hex);
                if (r.headers().firstValue("content-type").orElse("").contains("json") || r.headers().firstValue("content-type").orElse("").contains("html")
                        || r.headers().firstValue("content-type").orElse("").contains("text"))
                    d("  text: %s", text);
            } catch (Exception e) {
                d("%s -> FAILED %s", url, e);
            }
        }
    }
}
