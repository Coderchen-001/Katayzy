package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisEngineFallbackTest {
  @TempDir Path tempDir;

  private Path makeEngineDir() throws Exception {
    Path dir = Files.createDirectories(tempDir.resolve("engines").resolve("katago-trt"));
    Files.writeString(dir.resolve("katago.exe"), "fake engine");
    Files.writeString(dir.resolve("b10c384h6nbttflrs.bin.gz"), "fake weight");
    Files.writeString(dir.resolve("b10c384.cfg"), "rules = chinese");
    return dir;
  }

  @Test
  void fallbackBuildsB10C384AnalysisCommand() throws Exception {
    Path dir = makeEngineDir();
    String command = AnalysisEngine.buildFallbackAnalysisCommand(dir);
    assertNotNull(command);
    assertTrue(command.contains("katago.exe"));
    assertTrue(command.contains("analysis"));
    assertTrue(command.contains("b10c384h6nbttflrs.bin.gz"));
    assertTrue(command.contains("b10c384.cfg"));
    assertTrue(command.contains("-quit-without-waiting"));
  }

  @Test
  void fallbackReturnsNullWhenWeightOrConfigMissing() throws Exception {
    Path dir = Files.createDirectories(tempDir.resolve("partial"));
    Files.writeString(dir.resolve("katago.exe"), "fake engine");
    // weight present but config missing -> no usable companion profile
    Files.writeString(dir.resolve("b10c384h6nbttflrs.bin.gz"), "fake weight");
    assertNull(AnalysisEngine.buildFallbackAnalysisCommand(dir));
  }

  @Test
  void fallbackReturnsNullWhenEngineMissing() {
    assertNull(AnalysisEngine.buildFallbackAnalysisCommand(tempDir));
  }

  @Test
  void usableRejectsNullAndBlank() {
    assertFalse(AnalysisEngine.isUsableAnalysisCommand(null));
    assertFalse(AnalysisEngine.isUsableAnalysisCommand("   "));
  }

  @Test
  void usableRejectsDefaultPlaceholder() {
    assertFalse(
        AnalysisEngine.isUsableAnalysisCommand(
            "katago analysis -model model.bin.gz -config analysis.cfg -quit-without-waiting"));
  }

  @Test
  void usableRejectsMissingModelFile() {
    assertFalse(
        AnalysisEngine.isUsableAnalysisCommand(
            "katago analysis -model no-such-model.bin.gz -config analysis.cfg"));
  }

  @Test
  void usableRejectsMissingConfigFile() throws Exception {
    Path model = tempDir.resolve("model.bin.gz");
    Files.writeString(model, "x");
    assertFalse(
        AnalysisEngine.isUsableAnalysisCommand(
            "katago analysis -model " + model + " -config missing.cfg"));
  }

  @Test
  void usableAcceptsExistingModelAndConfig() throws Exception {
    Path model = tempDir.resolve("model.bin.gz");
    Path config = tempDir.resolve("analysis.cfg");
    Files.writeString(model, "x");
    Files.writeString(config, "x");
    String command = "katago analysis -model " + model + " -config " + config;
    assertTrue(AnalysisEngine.isUsableAnalysisCommand(command));
  }
}
