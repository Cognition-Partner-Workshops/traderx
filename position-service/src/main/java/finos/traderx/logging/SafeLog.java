package finos.traderx.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class SafeLog {
    private SafeLog() {}

    public static String value(Object value) {
        return String.valueOf(value).replace('\r', '_').replace('\n', '_');
    }

    public static String exception(Throwable error) {
        StringWriter details = new StringWriter();
        error.printStackTrace(new PrintWriter(details));
        return value(details);
    }
}
