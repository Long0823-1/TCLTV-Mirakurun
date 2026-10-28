package io.github.long0823.tclmirakurun;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class Root {
    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    static String capture(String command) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(process::destroy, 10, TimeUnit.MINUTES);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = process.getInputStream().read(buffer)) != -1) {
                if (output.size() + n > 1024 * 1024) throw new IOException("root output exceeds limit");
                output.write(buffer, 0, n);
            }
            int result = process.waitFor();
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (result != 0) throw new IOException(text + "\n終了コード: " + result);
            return text;
        } finally {
            timer.shutdownNow();
            process.destroy();
        }
    }
}
