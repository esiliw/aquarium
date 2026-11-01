/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Place names to coordinates (OpenStreetMap Nominatim), shared by /geotp and the world customisation screen. */
public final class Geocoder {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Pattern COORDS = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,; ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    public record Place(double lat, double lon, String name) {
    }

    private Geocoder() {
    }

    /** "55.6, 13.0" style input, or null if the text isn't a coordinate pair */
    public static Place parseCoordinates(String text) {
        Matcher m = COORDS.matcher(text);
        if (!m.matches()) return null;
        double lat = Double.parseDouble(m.group(1)), lon = Double.parseDouble(m.group(2));
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
        return new Place(lat, lon, null);
    }

    /** looks a place name up anywhere; null if nothing was found or the service couldn't be reached. Blocking. */
    public static Place find(String query) {
        return search(query, null);
    }

    /**
     * looks a place name up near a point first (about 60 km around it), and only searches the whole world
     * if nothing is found there - so "Klia" from Merseburg finds the stream, not a place on the other side of the planet.
     */
    public static Place findNear(String query, double lat, double lon) {
        double dLat = 0.55, dLon = 0.55 / Math.max(0.1, Math.cos(Math.toRadians(lat)));
        String box = String.format(java.util.Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=1",
                lon - dLon, Math.min(90, lat + dLat), lon + dLon, Math.max(-90, lat - dLat));
        Place near = search(query, box);
        return near != null ? near : search(query, null);
    }

    private static Place search(String query, String extra) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + (extra == null ? "" : extra);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Aquarium/" + DataEnv.version() + " (Minecraft mod; github.com/esiliw/aquarium)")
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            if (arr.isEmpty()) return null;
            JsonObject o = arr.get(0).getAsJsonObject();
            String name = o.has("display_name") ? o.get("display_name").getAsString() : query;
            return new Place(Double.parseDouble(o.get("lat").getAsString()), Double.parseDouble(o.get("lon").getAsString()), name);
        } catch (Exception e) {
            DataEnv.LOGGER.warn("Geocoding failed for {}", query, e);
            return null;
        }
    }
}
