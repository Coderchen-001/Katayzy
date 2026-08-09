package featurecat.lizzie.util;

import featurecat.lizzie.Lizzie;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KataGoRuntimeHelper {
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

  public static List<String> prepareBundledLaunchCommand(
      List<String> originalCommand, Path enginePath) {
    if (originalCommand == null) {
      return null;
    }
    List<String> launchCommand = new ArrayList<String>(originalCommand);
    if (Lizzie.config == null) {
      return launchCommand;
    }
    if (!looksLikeKataGoCommand(buildCommandLine(originalCommand))) {
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
   * benchmark result is already stored or if no engine is available.
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

    // 快速分析（小 visits）不再强制单线程覆盖：让 cfg 的 numSearchThreadsPerAnalysisThread
    // 生效。伴生进程（b10c384）同时服务快速分析与整盘精析（复用同一进程），
    // 16 线程对两者都更快（分析进程的线程池常驻，无启动开销）。
    return commandChanged ? buildCommandLine(commandParts) : engineCommand;
  }

  private static boolean looksLikeKataGoCommand(String engineCommand) {
    String normalized = engineCommand == null ? "" : engineCommand.toLowerCase(Locale.ROOT);
    return normalized.contains("katago");
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

}
