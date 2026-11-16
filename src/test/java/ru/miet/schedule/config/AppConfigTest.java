package ru.miet.schedule.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void emptyPropertiesGiveDefaults() {
        AppConfig c = AppConfig.of(new Properties());
        assertEquals(6, c.solver().maxPairsPerDay());
        assertEquals(1L, c.semesterId());
        assertEquals(8080, c.webPort());
    }

    @Test
    void valuesOverrideDefaults() {
        Properties p = new Properties();
        p.setProperty("solver.maxPairsPerDay", "5");
        p.setProperty("solver.weight.studentWindow", "12,5");
        p.setProperty("solver.timeLimitSeconds", "10");
        p.setProperty("web.port", "9000");
        AppConfig c = AppConfig.of(p);
        assertEquals(5, c.solver().maxPairsPerDay());
        assertEquals(12.5, c.solver().studentWindow());
        assertEquals(10_000L, c.solver().timeLimitMillis());
        assertEquals(9000, c.webPort());
    }

    @Test
    void badValuesGiveReadableErrors() {
        Properties p = new Properties();
        p.setProperty("solver.maxPairsPerDay", "много");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> AppConfig.of(p))
                .getMessage().contains("solver.maxPairsPerDay"));

        Properties port = new Properties();
        port.setProperty("web.port", "70000");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.of(port));

        Properties range = new Properties();
        range.setProperty("solver.maxPairsPerDay", "99");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.of(range));
    }

    @Test
    void bundledFileLoadsAndExternalFileOverrides() throws Exception {
        assertEquals(6, AppConfig.load(null).solver().maxPairsPerDay());

        Path file = Files.createTempFile("app", ".properties");

        try {
            Files.writeString(file, "solver.maxPairsPerDay=4\n");
            assertEquals(4, AppConfig.load(file).solver().maxPairsPerDay());
            assertEquals(8080, AppConfig.load(file).webPort());
        } finally {
            Files.delete(file);
        }

        assertThrows(IllegalArgumentException.class, () -> AppConfig.load(Path.of("no-such-file.properties")));
    }
}
