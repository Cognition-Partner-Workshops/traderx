package finos.traderx.positionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDependencyVersionsTest {
    private static void atLeast(String actual, int major, int minor) {
        String[] parts = actual.split("\\.");
        int actualMajor = Integer.parseInt(parts[0]);
        int actualMinor = Integer.parseInt(parts[1]);
        assertTrue(actualMajor > major || actualMajor == major && actualMinor >= minor,
                () -> actual + " must be >= " + major + "." + minor);
    }

    @Test
    void springBootIsPatched() {
        atLeast(SpringBootVersion.getVersion(), 3, 5);
    }

    @Test
    void h2IsPatched() {
        atLeast(org.h2.engine.Constants.FULL_VERSION, 2, 3);
    }
}
