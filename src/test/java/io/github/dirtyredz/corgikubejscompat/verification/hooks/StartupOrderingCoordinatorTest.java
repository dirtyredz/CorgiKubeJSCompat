package io.github.dirtyredz.corgikubejscompat.verification.hooks;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StartupOrderingCoordinatorTest {
    @Test void writerDeadlineDoesNotStartDuringPackBootstrap() throws Exception {
        Path output = Files.createTempDirectory(Path.of("build"), "coordinator-test-").toAbsolutePath();
        String classpath = Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(StartupOrderingHooks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dcompat.scenario=writer-first", "-cp", classpath, Probe.class.getName())
                .directory(output.toFile()).redirectErrorStream(true)
                .redirectOutput(output.resolve("console.log").toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Coordinator probe timed out");
            assertEquals(0, child.exitValue(), Files.readString(output.resolve("console.log")));
        } finally {
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Coordinator probe did not stop");
        }
    }

    public static final class Probe {
        public static void main(String[] args) {
            StartupOrderingHooks.start();
            boolean premature = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.getName().equals("compat-latch-coordinator") && thread.isAlive());
            if (premature) System.out.println("Writer deadline started before the initializer was held");
            // This probe deliberately never boots Minecraft; skip the runtime acceptance shutdown hook.
            Runtime.getRuntime().halt(premature ? 1 : 0);
        }
    }
}
