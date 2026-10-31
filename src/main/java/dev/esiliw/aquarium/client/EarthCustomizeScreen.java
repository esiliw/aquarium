/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.client;

import dev.esiliw.aquarium.data.EarthData;
import dev.esiliw.aquarium.data.GeoProjection;
import dev.esiliw.aquarium.data.Geocoder;
import dev.esiliw.aquarium.world.EarthChunkGenerator;
import dev.esiliw.aquarium.world.EarthSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** "Customize" for the Earth world type: scale, height scale, start location (like /geotp), projection and data source. */
public class EarthCustomizeScreen extends Screen {
    private final CreateWorldScreen parent;
    private final EarthSettings initial;

    private EditBox scaleBox, verticalBox, startBox;
    private GeoProjection.Type projection;
    private EarthData.Source source;
    private Button doneButton;
    private Component status = Component.empty();
    private boolean searching;

    public EarthCustomizeScreen(CreateWorldScreen parent, EarthSettings initial) {
        super(Component.translatable("aquarium.customize.title"));
        this.parent = parent;
        this.initial = initial;
        this.projection = initial.projectionType();
        this.source = initial.dataSource();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int labelX = cx - 155, fieldX = cx - 5, fieldW = 160;
        int y = 40;

        String oldScale = this.scaleBox != null ? this.scaleBox.getValue() : trim(this.initial.worldScale());
        String oldVertical = this.verticalBox != null ? this.verticalBox.getValue()
                : (this.initial.verticalScale() == EarthSettings.autoVerticalScale(this.initial.worldScale()) ? "" : trim(this.initial.verticalScale()));
        String oldStart = this.startBox != null ? this.startBox.getValue() : startText(this.initial);

        this.scaleBox = new EditBox(this.font, fieldX, y, fieldW, 20, Component.translatable("aquarium.customize.scale"));
        this.scaleBox.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        this.scaleBox.setValue(oldScale);
        this.addRenderableWidget(this.scaleBox);
        y += 26;

        this.verticalBox = new EditBox(this.font, fieldX, y, fieldW, 20, Component.translatable("aquarium.customize.vertical"));
        this.verticalBox.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        this.verticalBox.setHint(Component.translatable("aquarium.customize.vertical.hint").withStyle(ChatFormatting.DARK_GRAY));
        this.verticalBox.setValue(oldVertical);
        this.addRenderableWidget(this.verticalBox);
        y += 26;

        this.startBox = new EditBox(this.font, fieldX, y, fieldW, 20, Component.translatable("aquarium.customize.start"));
        this.startBox.setMaxLength(200);
        this.startBox.setHint(Component.translatable("aquarium.customize.start.hint").withStyle(ChatFormatting.DARK_GRAY));
        this.startBox.setValue(oldStart);
        this.addRenderableWidget(this.startBox);
        y += 30;

        this.addRenderableWidget(CycleButton.<GeoProjection.Type>builder(t -> Component.translatable("aquarium.customize.projection." + t.name().toLowerCase(Locale.ROOT)))
                .withValues(GeoProjection.Type.MERCATOR, GeoProjection.Type.EQUIRECTANGULAR)
                .withInitialValue(this.projection)
                .create(labelX, y, 310, 20, Component.translatable("aquarium.customize.projection"), (b, v) -> this.projection = v));
        y += 24;
        this.addRenderableWidget(CycleButton.<EarthData.Source>builder(t -> Component.translatable("aquarium.customize.data." + t.name().toLowerCase(Locale.ROOT)))
                .withValues(EarthData.Source.OPEN, EarthData.Source.TERRARIUM)
                .withInitialValue(this.source)
                .create(labelX, y, 310, 20, Component.translatable("aquarium.customize.data"), (b, v) -> this.source = v));

        this.doneButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.done())
                .bounds(cx - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(cx + 5, this.height - 28, 150, 20).build());
        this.doneButton.active = !this.searching;
    }

    private static String startText(EarthSettings s) {
        if (Math.abs(s.spawnLat() - EarthSettings.MALMO_LAT) < 1e-6 && Math.abs(s.spawnLon() - EarthSettings.MALMO_LON) < 1e-6) return "";
        return String.format(Locale.ROOT, "%.5f, %.5f", s.spawnLat(), s.spawnLon());
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private double scale() {
        try {
            double v = Double.parseDouble(this.scaleBox.getValue());
            return v >= 0.1 && v <= 100000 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double vertical(double scale) {
        String t = this.verticalBox.getValue().trim();
        if (t.isEmpty()) return EarthSettings.autoVerticalScale(scale);
        try {
            double v = Double.parseDouble(t);
            return v >= 0.1 && v <= 100000 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void done() {
        double scale = this.scale();
        if (scale <= 0) {
            this.status = Component.literal("Enter a scale between 0.1 and 100000 (blocks are 1:N metres).").withStyle(ChatFormatting.RED);
            return;
        }
        double vertical = this.vertical(scale);
        if (vertical <= 0) {
            this.status = Component.literal("Enter metres per block for height, or leave it empty for auto.").withStyle(ChatFormatting.RED);
            return;
        }
        String query = this.startBox.getValue().trim();
        if (query.isEmpty()) {
            this.apply(scale, vertical, EarthSettings.MALMO_LAT, EarthSettings.MALMO_LON);
            return;
        }
        Geocoder.Place coords = Geocoder.parseCoordinates(query);
        if (coords != null) {
            this.apply(scale, vertical, coords.lat(), coords.lon());
            return;
        }
        // a place name: look it up like /geotp, then create the world there
        this.searching = true;
        this.doneButton.active = false;
        this.status = Component.literal("Searching for \"" + query + "\" ...").withStyle(ChatFormatting.GRAY);
        CompletableFuture.supplyAsync(() -> Geocoder.find(query)).thenAccept(place -> this.minecraft.execute(() -> {
            this.searching = false;
            if (this.doneButton != null) this.doneButton.active = true;
            if (place == null) {
                this.status = Component.literal("Couldn't find \"" + query + "\". Try another name, or enter lat, lon.").withStyle(ChatFormatting.RED);
                return;
            }
            this.apply(scale, vertical, place.lat(), place.lon());
        }));
    }

    private void apply(double scale, double vertical, double lat, double lon) {
        if (this.projection == GeoProjection.Type.MERCATOR) lat = Math.max(-GeoProjection.MERCATOR_MAX_LAT, Math.min(GeoProjection.MERCATOR_MAX_LAT, lat));
        EarthSettings s = new EarthSettings(scale, vertical, 0, this.initial.seaLevel(), this.initial.minY(), this.initial.height(),
                lat, lon, this.projection, this.source, this.initial.osmSnapshot());
        this.parent.getUiState().updateDimensions((registries, dimensions) ->
                dimensions.replaceOverworldGenerator(registries, new EarthChunkGenerator(registries.lookupOrThrow(Registries.BIOME), s)));
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2, labelX = cx - 155;
        g.drawCenteredString(this.font, this.title, cx, 15, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("aquarium.customize.scale"), labelX, 46, 0xE0E0E0);
        g.drawString(this.font, Component.translatable("aquarium.customize.vertical"), labelX, 72, 0xE0E0E0);
        g.drawString(this.font, Component.translatable("aquarium.customize.start"), labelX, 98, 0xE0E0E0);

        int y = 40 + 26 + 26 + 30 + 24 + 30;
        double scale = this.scale();
        if (scale > 0) {
            double vertical = this.vertical(scale);
            if (vertical > 0) {
                int everest = this.initial.seaLevel() + (int) Math.floor(8849 / vertical);
                int top = this.initial.minY() + this.initial.height() - 1;
                int trench = this.initial.seaLevel() - (int) Math.ceil(10935 / vertical);
                String scaleNote = this.projection == GeoProjection.Type.MERCATOR
                        ? String.format(Locale.ROOT, "1 block = %s m at the equator, %s m in Malmö / central Europe",
                        trim(Math.round(scale * 100) / 100.0), trim(Math.round(scale * Math.cos(Math.toRadians(55.6)) * 100) / 100.0))
                        : String.format(Locale.ROOT, "1 block = %s m north-south everywhere", trim(scale));
                g.drawCenteredString(this.font, scaleNote, cx, y, 0xA0A0A0);
                y += 12;
                g.drawCenteredString(this.font, String.format(Locale.ROOT, "Mount Everest reaches y=%d%s", everest,
                        everest > top ? " - above the build limit, it will be cut off" : ""), cx, y, everest > top ? 0xFF6060 : 0xA0A0A0);
                y += 12;
                if (trench < this.initial.minY()) {
                    g.drawCenteredString(this.font, "The deepest sea floor is cut off at the bottom of the world", cx, y, 0xA0A0A0);
                    y += 12;
                }
            }
        }
        g.drawCenteredString(this.font, this.status, cx, this.height - 44, 0xFFFFFF);
    }
}
