package featurecat.lizzie.util;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.KataGoAutoSetupHelper.SetupSnapshot;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KataGoRuntimeHelper {
  private static final String NVIDIA_ENGINE_DIR = "windows-x64-nvidia";
  private static final String NVIDIA50_CUDA_ENGINE_DIR = "windows-x64-nvidia50-cuda";
  private static final String NVIDIA_TRT_ENGINE_DIR = "windows-x64-nvidia-tensorrt";
  private static final String NVIDIA50_TRT_ENGINE_DIR = "windows-x64-nvidia50-trt";
  private static final String NVIDIA_BACKEND = "nvidia";
  private static final String NVIDIA50_CUDA_BACKEND = "nvidia50-cuda";
  private static final String NVIDIA_TRT_BACKEND = "nvidia-tensorrt";
  private static final String NVIDIA50_TRT_BACKEND = "nvidia50-trt";
  private static final int MAX_APPLE_ANALYSIS_THREADS = 8;
  private static volatile boolean benchmarkEngineSyncSuppressed = false;

  private KataGoRuntimeHelper() {}

  public static final class BenchmarkResult {
    public final int recommendedThreads;
    public final int currentThreads;
    public final String backendLabel;
    public final String summary;
    public final long completedAtMillis;

    private BenchmarkResult(
        int recommendedThreads,
        int currentThreads,
        String backendLabel,
        String summary,
        long completedAtMillis) {
      this.recommendedThreads = recommendedThreads;
      this.currentThreads = currentThreads;
      this.backendLabel = backendLabel;
      this.summary = summary;
      this.completedAtMillis = completedAtMillis;
    }
  }

  private static final class AnalysisThreadProfile {
    public final int numAnalysisThreads;
    public final int numSearchThreadsPerAnalysisThread;

    private AnalysisThreadProfile(int numAnalysisThreads, int numSearchThreadsPerAnalysisThread) {
      this.numAnalysisThreads = numAnalysisThreads;
      this.numSearchThreadsPerAnalysisThread = numSearchThreadsPerAnalysisThread;
    }
  }

  public static Path resolveCommandExecutable(List<String> commands) {
    if (commands == null || commands.isEmpty()) {
      return null;
    }
    String executable = commands.get(0);
    if (executable == null || executable.trim().isEmpty()) {
      return null;
    }
    Path resolved = Utils.resolveExistingExecutable(executable);
    if (resolved != null) {
      return resolved.toAbsolutePath().normalize();
    }
    try {
      Path direct = Paths.get(executable);
      if (!direct.isAbsolute()) {
        direct = direct.toAbsolutePath();
      }
      return direct.normalize();
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isNvidiaBundledPath(Path enginePath) {
    return resolveNvidiaBackend(enginePath) != null;
  }

  private static String resolveNvidiaBackend(Path enginePath) {
    if (enginePath == null) {
      return null;
    }
    String normalized = enginePath.toAbsolutePath().normalize().toString().replace('\\', '/');
    String normalizedLower = normalized.toLowerCase(Locale.ROOT);
    if (normalizedLower.contains("/" + NVIDIA_TRT_ENGINE_DIR + "/")) {
      return NVIDIA_TRT_BACKEND;
    }
    if (normalizedLower.contains("/" + NVIDIA50_TRT_ENGINE_DIR + "/")) {
      return NVIDIA_TRT_BACKEND;
    }
    if (normalizedLower.contains("/" + NVIDIA50_CUDA_ENGINE_DIR + "/")) {
      return NVIDIA50_CUDA_BACKEND;
    }
    if (normalizedLower.contains("/" + NVIDIA_ENGINE_DIR + "/")) {
      return NVIDIA_BACKEND;
    }
    return null;
  }

  public static List<String> prepareBundledLaunchCommand(
      List<String> originalCommand, Path enginePath) {
    if (originalCommand == null) {
      return null;
    }
    List<String> launchCommand = new ArrayList<String>(originalCommand);
    if (enginePath == null || Lizzie.config == null) {
      return launchCommand;
    }
    if (!Config.isBundledKataGoExecutable(enginePath)) {
      return launchCommand;
    }
    appendAnalysisPvLenOverride(launchCommand);
    return launchCommand;
  }

  public static BenchmarkResult getStoredBenchmarkResult() {
    if (Lizzie.config == null || Lizzie.config.uiConfig == null) {
      return null;
    }
    int recommended = Lizzie.config.uiConfig.optInt("katago-benchmark-threads", 0);
    if (recommended <= 0) {
      return null;
    }
    return new BenchmarkResult(
        recommended,
        Lizzie.config.uiConfig.optInt("katago-benchmark-current-threads", 0),
        Lizzie.config.uiConfig.optString("katago-benchmark-backend", "").trim(),
        Lizzie.config.uiConfig.optString("katago-benchmark-summary", "").trim(),
        Lizzie.config.uiConfig.optLong("katago-benchmark-updated-at", 0L));
  }

  public static boolean isBenchmarkEngineSyncSuppressed() {
    return benchmarkEngineSyncSuppressed;
  }

  /**
   * Run a one-time KataGo benchmark on the first launch so default thread counts reflect the actual
   * hardware. Shows a non-modal notification to the user while the benchmark runs. No-op if a
   * benchmark result is already stored, if no engine is available, or on Apple Silicon (handled by
   * {@link #startAppleSiliconAutoOptimizationAsync()}).
   */
  public static String optimizeAnalysisEngineCommand(
      String engineCommand, int maxVisits, boolean isBatchAnalysisMode) {
    return optimizeAnalysisEngineCommand(engineCommand, maxVisits, isBatchAnalysisMode, false);
  }

  public static String optimizeAnalysisEngineCommand(
      String engineCommand,
      int maxVisits,
      boolean isBatchAnalysisMode,
      boolean wholeGameThroughput) {
    if (engineCommand == null || engineCommand.trim().isEmpty()) {
      return engineCommand;
    }

    List<String> commandParts = Utils.splitCommand(engineCommand);
    if (commandParts.isEmpty()) {
      return engineCommand;
    }

    boolean hasSearchThreadOverride =
        hasOverrideConfigKey(commandParts, "numSearchThreadsPerAnalysisThread")
            || hasOverrideConfigKey(commandParts, "numSearchThreads");
    boolean hasAnalysisThreadOverride = hasOverrideConfigKey(commandParts, "numAnalysisThreads");
    boolean commandChanged = false;
    if (looksLikeKataGoCommand(engineCommand)) {
      commandChanged = appendAnalysisPvLenOverride(commandParts);
    }

    if (wholeGameThroughput && looksLikeKataGoCommand(engineCommand)) {
      AnalysisThreadProfile profile = resolveWholeGameAnalysisProfile();
      if (!hasAnalysisThreadOverride) {
        appendOverrideConfig(commandParts, "numAnalysisThreads=" + profile.numAnalysisThreads);
        commandChanged = true;
      }
      if (!hasSearchThreadOverride) {
        appendOverrideConfig(
            commandParts,
            "numSearchThreadsPerAnalysisThread=" + profile.numSearchThreadsPerAnalysisThread);
        commandChanged = true;
      }
      return commandChanged ? buildCommandLine(commandParts) : engineCommand;
    }

    if (shouldUseAppleSiliconAnalysisProfile(engineCommand)) {
      AnalysisThreadProfile profile =
          resolveAppleSiliconAnalysisProfile(maxVisits, isBatchAnalysisMode);
      if (!hasAnalysisThreadOverride) {
        appendOverrideConfig(commandParts, "numAnalysisThreads=" + profile.numAnalysisThreads);
        commandChanged = true;
      }
      if (!hasSearchThreadOverride) {
        appendOverrideConfig(
            commandParts,
            "numSearchThreadsPerAnalysisThread=" + profile.numSearchThreadsPerAnalysisThread);
        commandChanged = true;
      }
      return buildCommandLine(commandParts);
    }

    // 快速分析（小 visits）不再强制单线程覆盖：让 cfg 的 numSearchThreadsPerAnalysisThread
    // 生效。伴生进程（b10c384）同时服务快速分析与整盘精析（复用同一进程），
    // 16 线程对两者都更快（分析进程的线程池常驻，无启动开销）。
    return commandChanged ? buildCommandLine(commandParts) : engineCommand;
  }

  private static boolean shouldUseAppleSiliconAnalysisProfile(String engineCommand) {
    if (!isAppleSiliconHost()) {
      return false;
    }
    String normalized = engineCommand == null ? "" : engineCommand.toLowerCase(Locale.ROOT);
    if (!normalized.contains(" analysis")) {
      return false;
    }
    return Config.isBundledKataGoCommand(engineCommand);
  }

  private static boolean looksLikeKataGoCommand(String engineCommand) {
    String normalized = engineCommand == null ? "" : engineCommand.toLowerCase(Locale.ROOT);
    return normalized.contains("katago");
  }

  private static AnalysisThreadProfile resolveAppleSiliconAnalysisProfile(
      int maxVisits, boolean isBatchAnalysisMode) {
    int totalThreadBudget = Math.max(4, Math.min(16, Utils.getRecommendedKataGoThreads()));
    int effectiveVisits = Math.max(1, maxVisits);
    int perAnalysisThread;
    int maxParallelAnalyses;

    if (effectiveVisits <= 8) {
      perAnalysisThread = 1;
      maxParallelAnalyses = MAX_APPLE_ANALYSIS_THREADS;
    } else if (effectiveVisits <= 36) {
      perAnalysisThread = 2;
      maxParallelAnalyses = 6;
    } else if (effectiveVisits <= 100) {
      perAnalysisThread = 2;
      maxParallelAnalyses = 5;
    } else if (effectiveVisits <= 220) {
      perAnalysisThread = 3;
      maxParallelAnalyses = 4;
    } else {
      perAnalysisThread = Math.min(4, Math.max(2, totalThreadBudget / 3));
      maxParallelAnalyses = 3;
    }

    if (isBatchAnalysisMode && effectiveVisits >= 100) {
      perAnalysisThread = Math.max(perAnalysisThread, 3);
      maxParallelAnalyses = Math.min(maxParallelAnalyses, 4);
    }

    int numAnalysisThreads =
        Math.max(
            2, Math.min(maxParallelAnalyses, Math.max(1, totalThreadBudget / perAnalysisThread)));

    if (effectiveVisits <= 12 && totalThreadBudget >= 6) {
      numAnalysisThreads =
          Math.max(numAnalysisThreads, Math.min(MAX_APPLE_ANALYSIS_THREADS, totalThreadBudget));
    }

    return new AnalysisThreadProfile(numAnalysisThreads, perAnalysisThread);
  }

  private static AnalysisThreadProfile resolveWholeGameAnalysisProfile() {
    int totalThreadBudget = Math.max(2, Math.min(24, Utils.getRecommendedKataGoThreads()));
    int numAnalysisThreads = Math.max(2, Math.min(8, totalThreadBudget / 2));
    int perAnalysisThread = Math.max(1, Math.min(2, totalThreadBudget / numAnalysisThreads));
    return new AnalysisThreadProfile(numAnalysisThreads, perAnalysisThread);
  }

  private static void appendOverrideConfig(List<String> command, String keyValue) {
    if (command == null || keyValue == null || keyValue.trim().isEmpty()) {
      return;
    }
    String normalizedKey = overrideConfigKey(keyValue);

    for (int i = 0; i < command.size(); i++) {
      if (!"-override-config".equals(command.get(i))) {
        continue;
      }

      if (i + 1 >= command.size()) {
        command.add(keyValue);
        return;
      }

      String existing = command.get(i + 1);
      if (!normalizedKey.isEmpty() && containsOverrideConfigKey(existing, normalizedKey)) {
        return;
      }
      if (existing == null || existing.trim().isEmpty()) {
        command.set(i + 1, keyValue);
      } else {
        command.set(i + 1, existing + "," + keyValue);
      }
      return;
    }

    command.add("-override-config");
    command.add(keyValue);
  }

  private static String findOverrideConfigValue(List<String> command, String key) {
    if (command == null || key == null || key.trim().isEmpty()) {
      return "";
    }
    String expectedKey = key.trim();
    for (int i = 0; i + 1 < command.size(); i++) {
      if (!"-override-config".equals(command.get(i))) {
        continue;
      }
      String overrides = command.get(i + 1);
      if (overrides == null) {
        continue;
      }
      for (String entry : overrides.split(",")) {
        int separator = entry.indexOf('=');
        if (separator <= 0) {
          continue;
        }
        if (expectedKey.equalsIgnoreCase(entry.substring(0, separator).trim())) {
          return entry.substring(separator + 1).trim();
        }
      }
    }
    return "";
  }

  private static void setOverrideConfig(List<String> command, String keyValue) {
    if (command == null || keyValue == null || keyValue.trim().isEmpty()) {
      return;
    }
    String normalizedKey = overrideConfigKey(keyValue);
    if (normalizedKey.isEmpty()) {
      return;
    }
    for (int i = 0; i < command.size(); i++) {
      if (!"-override-config".equals(command.get(i))) {
        continue;
      }
      if (i + 1 >= command.size()) {
        command.add(keyValue);
        return;
      }
      String existing = command.get(i + 1);
      List<String> entries = new ArrayList<String>();
      boolean replaced = false;
      if (existing != null && !existing.trim().isEmpty()) {
        for (String entry : existing.split(",")) {
          if (normalizedKey.equals(overrideConfigKey(entry))) {
            if (!replaced) {
              entries.add(keyValue);
              replaced = true;
            }
          } else if (!entry.trim().isEmpty()) {
            entries.add(entry.trim());
          }
        }
      }
      if (!replaced) {
        entries.add(keyValue);
      }
      command.set(i + 1, String.join(",", entries));
      return;
    }
    command.add("-override-config");
    command.add(keyValue);
  }

  private static boolean appendAnalysisPvLenOverride(List<String> command) {
    int pvLen = resolveAnalysisPvLenOverride();
    if (pvLen <= 0 || hasOverrideConfigKey(command, "analysisPVLen")) {
      return false;
    }
    appendOverrideConfig(command, "analysisPVLen=" + pvLen);
    return true;
  }

  static int resolveAnalysisPvLenOverride() {
    if (Lizzie.config == null) {
      return 15;
    }
    return Math.max(0, Lizzie.config.limitBranchLength);
  }

  private static boolean hasOverrideConfigKey(List<String> command, String key) {
    if (command == null || key == null || key.trim().isEmpty()) {
      return false;
    }
    String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < command.size(); i++) {
      if (!"-override-config".equals(command.get(i)) || i + 1 >= command.size()) {
        continue;
      }
      String overrideValue = command.get(i + 1);
      if (overrideValue == null || overrideValue.trim().isEmpty()) {
        continue;
      }
      if (containsOverrideConfigKey(overrideValue, normalizedKey)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsOverrideConfigKey(String overrideValue, String normalizedKey) {
    if (overrideValue == null || normalizedKey == null || normalizedKey.trim().isEmpty()) {
      return false;
    }
    for (String entry : overrideValue.split(",")) {
      String trimmed = entry == null ? "" : entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int eqIndex = trimmed.indexOf('=');
      String entryKey = eqIndex >= 0 ? trimmed.substring(0, eqIndex).trim() : trimmed.trim();
      if (entryKey.toLowerCase(Locale.ROOT).equals(normalizedKey)) {
        return true;
      }
    }
    return false;
  }

  private static String overrideConfigKey(String keyValue) {
    String trimmed = keyValue == null ? "" : keyValue.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    int eqIndex = trimmed.indexOf('=');
    String key = eqIndex >= 0 ? trimmed.substring(0, eqIndex).trim() : trimmed;
    return key.toLowerCase(Locale.ROOT);
  }

  private static String buildCommandLine(List<String> commands) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < commands.size(); i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(quoteCommandToken(commands.get(i)));
    }
    return builder.toString();
  }

  private static String quoteCommandToken(String token) {
    if (token == null) {
      return "\"\"";
    }
    String trimmed = token.trim();
    if (trimmed.isEmpty()) {
      return "\"\"";
    }
    if (trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\t') >= 0 || trimmed.indexOf('"') >= 0) {
      return "\"" + trimmed.replace("\"", "\\\"") + "\"";
    }
    return trimmed;
  }

  private static boolean isAppleSiliconHost() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    return (osName.contains("mac") || osName.contains("darwin"))
        && (arch.contains("arm64") || arch.contains("aarch64"));
  }

  private static boolean isAppleSiliconOptimizationEligible(SetupSnapshot snapshot) {
    if (!isAppleSiliconHost() || snapshot == null) {
      return false;
    }
    if (!snapshot.hasEngine() || !snapshot.hasConfigs() || !snapshot.hasWeight()) {
      return false;
    }
    String enginePath =
        snapshot.enginePath.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    return enginePath.contains("macos-arm64");
  }

}
