package finos.traderx.tradeprocessor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.regex.Pattern;
import okio.Buffer;

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

    @Test
    void okhttpIsPatched() {
        atLeast(okhttp3.OkHttp.VERSION, 4, 12);
    }

    @Test
    void okioIsPatched() {
        String location = Buffer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        var matcher = Pattern.compile("okio(?:-jvm)?-(\\d+\\.\\d+\\.\\d+)\\.jar").matcher(location);
        assertTrue(matcher.find(), () -> "Cannot identify resolved Okio version from " + location);
        atLeast(matcher.group(1), 3, 6);
    }
}
