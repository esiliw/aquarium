package dev.esiliw.aquarium;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mixins target vanilla methods by name; make sure those exist in this Minecraft version (fails the build otherwise). */
public class MixinTargetsTest {
    static boolean has(String cls, String method, int params) throws Exception {
        Class<?> c = Class.forName(cls, false, MixinTargetsTest.class.getClassLoader());
        for (Method m : c.getDeclaredMethods()) if (m.getName().equals(method) && m.getParameterCount() == params) return true;
        System.out.println("DIAG methods of " + cls + ": " + Arrays.toString(Arrays.stream(c.getDeclaredMethods()).map(Method::getName).toArray()));
        return false;
    }

    @Test
    void targetsExist() throws Exception {
        assertTrue(has("net.minecraft.client.gui.screens.worldselection.WorldCreationUiState", "getPresetEditor", 0), "WorldCreationUiState.getPresetEditor");
        assertTrue(has("net.minecraft.client.gui.screens.worldselection.WorldCreationUiState", "getWorldType", 0), "WorldCreationUiState.getWorldType");
        assertTrue(has("net.minecraft.server.MinecraftServer", "setInitialSpawn", 4), "MinecraftServer.setInitialSpawn");
    }
}
