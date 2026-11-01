package dev.esiliw.aquarium.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AwsElevationTest {
    static final int S = AwsElevation.SIZE;

    @Test
    void seamIsRemovedCliffIsKept() {
        float[] t = new float[S * S];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) t[x + y * S] = x < 100 ? 6900f : 2000f;   // a 4900 m cliff at x = 100
        for (int x = 0; x < S; x++) { t[x + 181 * S] -= 4000f; t[x + 182 * S] -= 5000f; }                // a 2-pixel crack along a row
        t[50 + 20 * S] = -32768f;
        t[200 + 60 * S] = 2500f;                                                                           // a sharp peak                                                                          // a no-data hole
        AwsElevation.fillHoles(t);
        AwsElevation.despike(t);
        assertEquals(6900f, t[50 + 181 * S], 1f);
        assertEquals(6900f, t[50 + 182 * S], 1f);
        assertEquals(2000f, t[150 + 182 * S], 1f);
        assertEquals(6900f, t[50 + 20 * S], 1f);
        assertEquals(2500f, t[200 + 60 * S], 1f);   // peaks stay
        assertEquals(6900f, t[99 + 50 * S], 1f);    // both sides of the cliff untouched
        assertEquals(2000f, t[100 + 50 * S], 1f);
    }
}
