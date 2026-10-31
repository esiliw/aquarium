/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Port of the /geotp and /geotool commands.
 */
package dev.esiliw.aquarium.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.esiliw.aquarium.Aquarium;
import dev.esiliw.aquarium.world.EarthChunkGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class GeoCommands {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private GeoCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("geotp")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("lat", DoubleArgumentType.doubleArg(-90, 90))
                        .then(Commands.argument("lon", DoubleArgumentType.doubleArg(-180, 180))
                                .executes(ctx -> teleportToCoords(ctx, DoubleArgumentType.getDouble(ctx, "lat"), DoubleArgumentType.getDouble(ctx, "lon")))))
                .then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> teleportToPlace(ctx, StringArgumentType.getString(ctx, "place")))));

        dispatcher.register(Commands.literal("geotool")
                .executes(GeoCommands::whereAmI)
                .then(Commands.literal("where").executes(GeoCommands::whereAmI)));
    }

    private static EarthChunkGenerator earth(CommandSourceStack src) {
        if (src.getLevel().getChunkSource().getGenerator() instanceof EarthChunkGenerator g) return g;
        src.sendFailure(Component.literal("This dimension is not an Aquarium Earth world."));
        return null;
    }

    private static int teleportToCoords(CommandContext<CommandSourceStack> ctx, double lat, double lon) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        EarthChunkGenerator g = earth(src);
        if (g == null) return 0;
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Teleporting to %.5f, %.5f ...", lat, lon)).withStyle(ChatFormatting.GRAY), false);
        teleport(src, player, g, lat, lon, null);
        return 1;
    }

    private static int teleportToPlace(CommandContext<CommandSourceStack> ctx, String place) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        EarthChunkGenerator g = earth(src);
        if (g == null) return 0;
        src.sendSuccess(() -> Component.literal("Searching for \"" + place + "\" ...").withStyle(ChatFormatting.GRAY), false);
        CompletableFuture.supplyAsync(() -> geocode(place), Aquarium.IO_EXECUTOR).thenAccept(result -> {
            if (result == null) {
                src.getServer().execute(() -> src.sendFailure(Component.literal("Couldn't find \"" + place + "\".")));
                return;
            }
            teleport(src, player, g, result.lat, result.lon, result.name);
        });
        return 1;
    }

    private static void teleport(CommandSourceStack src, ServerPlayer player, EarthChunkGenerator g, double lat, double lon, String name) {
        // sample the terrain off-thread (may download data), then move the player on the server thread
        CompletableFuture.supplyAsync(() -> {
            int x = (int) Math.floor(g.projection().blockX(lon));
            int z = (int) Math.floor(g.projection().blockZ(lat));
            dev.esiliw.aquarium.world.TerrainModel.Column c = g.column(x, z);
            int y = Math.max(c.top(), c.waterTop()) + 2;
            return new int[]{x, y, z};
        }, Aquarium.IO_EXECUTOR).thenAccept(p -> src.getServer().execute(() -> {
            ServerLevel level = src.getLevel();
            level.getChunk(new ChunkPos(p[0] >> 4, p[2] >> 4).getWorldPosition());
            player.teleportTo(level, p[0] + 0.5, p[1], p[2] + 0.5, player.getYRot(), player.getXRot());
            String where = name != null ? name : String.format(Locale.ROOT, "%.5f, %.5f", lat, lon);
            src.sendSuccess(() -> Component.literal("Teleported to " + where).withStyle(ChatFormatting.GREEN), false);
        })).exceptionally(t -> {
            Aquarium.LOGGER.error("geotp failed", t);
            src.getServer().execute(() -> src.sendFailure(Component.literal("Teleport failed: " + t.getMessage())));
            return null;
        });
    }

    private static int whereAmI(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        EarthChunkGenerator g = earth(src);
        if (g == null) return 0;
        double x = src.getPosition().x, z = src.getPosition().z;
        double lat = g.projection().lat(z), lon = g.projection().lon(x);
        String coords = String.format(Locale.ROOT, "%.6f, %.6f", lat, lon);
        String maps = String.format(Locale.ROOT, "https://www.openstreetmap.org/#map=15/%.6f/%.6f", lat, lon);
        src.sendSuccess(() -> Component.literal("You are at ").append(Component.literal(coords).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, maps))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open in OpenStreetMap"))))),
                false);
        CompletableFuture.supplyAsync(() -> {
                    dev.esiliw.aquarium.world.TerrainModel.Column c = g.column((int) Math.floor(x), (int) Math.floor(z));
                    return String.format(Locale.ROOT, "Elevation %.0f m, land cover: %s%s, scale here 1:%s (%s)", c.elevation(),
                            c.cover().name().toLowerCase(Locale.ROOT), c.ocean() ? " (ocean)" : c.lake() ? " (lake)" : "",
                            EarthChunkGenerator.trim(Math.round(g.projection().metersPerBlock(lat) * 100) / 100.0),
                            g.projection().type().name().toLowerCase(Locale.ROOT));
                }, Aquarium.IO_EXECUTOR)
                .thenAccept(msg -> src.getServer().execute(() -> src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GRAY), false)));
        return 1;
    }

    private record Place(double lat, double lon, String name) {
    }

    private static Place geocode(String query) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Aquarium/" + Aquarium.VERSION + " (Minecraft mod, Fabric port of Terrarium)")
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            if (arr.isEmpty()) return null;
            JsonObject o = arr.get(0).getAsJsonObject();
            String name = o.has("display_name") ? o.get("display_name").getAsString() : query;
            return new Place(Double.parseDouble(o.get("lat").getAsString()), Double.parseDouble(o.get("lon").getAsString()), name);
        } catch (Exception e) {
            Aquarium.LOGGER.warn("Geocoding failed for {}", query, e);
            return null;
        }
    }
}
