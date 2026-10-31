package dev.esiliw.aquarium.mixin;

import dev.esiliw.aquarium.Aquarium;
import dev.esiliw.aquarium.client.EarthPresetEditor;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the Earth world type a "Customize" button (vanilla only has editors for flat and single-biome worlds). */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Shadow
    public abstract WorldCreationUiState.WorldTypeEntry getWorldType();

    @Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
    private void aquarium$earthEditor(CallbackInfoReturnable<PresetEditor> cir) {
        Holder<WorldPreset> preset = this.getWorldType().preset();
        if (preset != null && preset.is(Aquarium.EARTH_PRESET)) cir.setReturnValue(EarthPresetEditor.INSTANCE);
    }
}
