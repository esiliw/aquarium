/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.client;

import dev.esiliw.aquarium.world.EarthChunkGenerator;
import dev.esiliw.aquarium.world.EarthSettings;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;

/** Opens {@link EarthCustomizeScreen} from the world creation screen's "Customize" button. */
public final class EarthPresetEditor {
    public static final PresetEditor INSTANCE = (parent, context) -> {
        EarthSettings current = context.selectedDimensions().overworld() instanceof EarthChunkGenerator g ? g.settings : EarthSettings.defaults();
        return new EarthCustomizeScreen(parent, current);
    };

    private EarthPresetEditor() {
    }
}
