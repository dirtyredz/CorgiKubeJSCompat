package io.github.dirtyredz.corgikubejscompat.verification.hooks;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StartupOrderingCoordinatorTest {
    private static Path javaLauncher(String osName) {
        String executable = osName.startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    @Test void javaLauncherUsesPlatformExecutableName() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        assertEquals(bin.resolve("java.exe"), javaLauncher("Windows 11"));
        assertEquals(bin.resolve("java"), javaLauncher("Linux"));
        assertEquals(bin.resolve("java"), javaLauncher("Mac OS X"));
        assertEquals(bin.resolve("java"), javaLauncher("Darwin"));
        assertTrue(Files.isExecutable(javaLauncher(System.getProperty("os.name"))));
    }

    @Test void writerDeadlineDoesNotStartDuringPackBootstrap() throws Exception {
        Path output = Files.createTempDirectory(Path.of("build"), "coordinator-test-").toAbsolutePath();
        String classpath = Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(StartupOrderingHooks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(javaLauncher(System.getProperty("os.name")).toString(),
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
