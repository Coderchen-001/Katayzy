package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class KataGoRuntimeHelperTest {

  @Test
  void bundledLaunchCommandAddsPvLengthOverride() throws Exception {
    Path tempRoot = Files.createTempDirectory("katago-helper-bundled-command");
    Path enginePath =
        touch(
            tempRoot
                .resolve("engines")
                .resolve("katago")
                .resolve("windows-x64")
                .resolve("katago.exe"));
    Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

    withConfig(
        runtimeWorkDirectory,
        () -> {
          Lizzie.config.limitBranchLength = 32;
          List<String> command =
              KataGoRuntimeHelper.prepareBundledLaunchCommand(
                  Arrays.asList(enginePath.toString(), "gtp", "-config", "gtp.cfg"), enginePath);

          assertTrue(command.contains("-override-config"));
          String overrides = command.get(command.indexOf("-override-config") + 1);
          assertTrue(overrides.contains("analysisPVLen=32"));
        });
  }

  @Test
  void katagoAnalysisCommandReceivesPvLengthOverride() throws Exception {
    Path runtimeWorkDirectory = Files.createTempDirectory("katago-helper-pvlen");
    withConfig(
        runtimeWorkDirectory,
        () -> {
          Lizzie.config.limitBranchLength = 28;
          String command =
              KataGoRuntimeHelper.optimizeAnalysisEngineCommand(
                  "katago analysis -model model.bin.gz -config analysis.cfg", 100, false);

          assertTrue(command.contains("analysisPVLen=28"));
        });
  }

  @Test
  void nonKataGoAnalysisCommandKeepsOriginalText() throws Exception {
    Path runtimeWorkDirectory = Files.createTempDirectory("katago-helper-nonkatago");
    withConfig(
        runtimeWorkDirectory,
        () ->
            assertEquals(
                "leelaz --gtp",
                KataGoRuntimeHelper.optimizeAnalysisEngineCommand("leelaz --gtp", 100, false)));
  }

  private static void withConfig(Path runtimeWorkDirectory, ThrowingRunnable action)
      throws Exception {
    Config previousConfig = Lizzie.config;
    String previousUserDir = System.getProperty("user.dir");
    try {
      System.setProperty("user.dir", runtimeWorkDirectory.toString());
      Lizzie.config = createTestConfig(runtimeWorkDirectory);
      action.run();
    } finally {
      if (previousUserDir == null) {
        System.clearProperty("user.dir");
      } else {
        System.setProperty("user.dir", previousUserDir);
      }
      Lizzie.config = previousConfig;
    }
  }

  @Test
  void wholeGameAnalysisUsesParallelPositionProfile() throws Exception {
    Path runtimeWorkDirectory = Files.createTempDirectory("katago-helper-whole-game");
    withConfig(
        runtimeWorkDirectory,
        () -> {
          String command =
              KataGoRuntimeHelper.optimizeAnalysisEngineCommand(
                  "katago analysis -model model.bin.gz -config analysis.cfg", 500, false, true);

          assertTrue(command.contains("numAnalysisThreads="));
          assertTrue(command.contains("numSearchThreadsPerAnalysisThread="));
          assertTrue(command.contains("analysisPVLen="));
        });
  }

  @Test
  void wholeGameAnalysisRespectsExplicitThreadOverrides() throws Exception {
    Path runtimeWorkDirectory = Files.createTempDirectory("katago-helper-whole-game-override");
    withConfig(
        runtimeWorkDirectory,
        () -> {
          String command =
              KataGoRuntimeHelper.optimizeAnalysisEngineCommand(
                  "katago analysis -model model.bin.gz -config analysis.cfg "
                      + "-override-config numAnalysisThreads=3,numSearchThreadsPerAnalysisThread=4",
                  500,
                  false,
                  true);

          assertEquals(1, occurrences(command, "numAnalysisThreads=3"));
          assertEquals(1, occurrences(command, "numSearchThreadsPerAnalysisThread=4"));
        });
  }

  private static Config createTestConfig(Path runtimeWorkDirectory) {
    Config config = ConfigTestHelper.createForTests(runtimeWorkDirectory);
    config.config = new JSONObject();
    config.leelazConfig = new JSONObject();
    config.uiConfig = new JSONObject();
    config.config.put("leelaz", config.leelazConfig);
    config.config.put("ui", config.uiConfig);
    return config;
  }

  private static Path touch(Path file) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.write(file, new byte[0]);
  }

  private static int occurrences(String text, String value) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(value, index)) >= 0) {
      count++;
      index += value.length();
    }
    return count;
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
