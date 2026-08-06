package featurecat.lizzie.util;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.KataGoAutoSetupHelper.SetupSnapshot;
import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class KataGoRuntimeHelper {
  private static final String NVIDIA_ENGINE_DIR = "windows-x64-nvidia";
  private static final String NVIDIA50_CUDA_ENGINE_DIR = "windows-x64-nvidia50-cuda";
  private static final String NVIDIA_TRT_ENGINE_DIR = "windows-x64-nvidia-tensorrt";
  private static final String NVIDIA50_TRT_ENGINE_DIR = "windows-x64-nvidia50-trt";
  private static final String NVIDIA_BACKEND = "nvidia";
  private static final String NVIDIA50_CUDA_BACKEND = "nvidia50-cuda";
  private static final String NVIDIA_TRT_BACKEND = "nvidia-tensorrt";
  private static final String NVIDIA50_TRT_BACKEND = "nvidia50-trt";
  private static final String OPENCL_BACKEND = "opencl";
  private static final String ENGINE_BACKEND_MARKER_NAME = "lizzieyzy-next-engine-backend.txt";
  private static final String NVIDIA_RUNTIME_ROOT = "nvidia-runtime";
  private static final String NVIDIA_RUNTIME_CACHE_DIR = "cache";
  private static final String NVIDIA_CUDA_CACHE_DIR = "cuda";
  private static final String NVIDIA_TENSORRT_TEMP_DIR = "temp";
  private static final String BUNDLED_HOME_DATA_DIR = "katago-home";
  private static final String OPENCL_FP32_HOME_DATA_DIR = "katago-home-opencl-fp32";
  private static final String OPENCL_FP32_COMPATIBILITY_MARKER = "compatibility-signature.txt";
  private static final String OPENCL_TUNING_CACHE_GENERATION_MARKER =
      "lizzie-opencl-tuning-generation.txt";
  private static final String OPENCL_TUNING_CACHE_GENERATION = "serialized-launch-v1";
  private static final String OPENCL_NVIDIA_DRIVER_VERSION_PROPERTY =
      "lizzie.opencl.nvidiaDriverVersion";
  private static final int WINDOWS_FAST_FAIL_EXIT_CODE = (int) 0xC0000409L;
  private static final List<List<String>> REQUIRED_NVIDIA_CUDA12_1_CUDNN8_RUNTIME_DLL_GROUPS =
      Arrays.asList(
          Arrays.asList("cudart64_12.dll"),
          Arrays.asList("cublas64_12.dll"),
          Arrays.asList("cublasLt64_12.dll"),
          Arrays.asList("cudnn64_8.dll"),
          Arrays.asList("nvJitLink*.dll"),
          Arrays.asList("zlibwapi.dll", "libz.dll", "z.dll"));
  private static final List<List<String>> REQUIRED_NVIDIA_CUDA12_1_CUDNN9_RUNTIME_DLL_GROUPS =
      Arrays.asList(
          Arrays.asList("cudart64_12.dll"),
          Arrays.asList("cublas64_12.dll"),
          Arrays.asList("cublasLt64_12.dll"),
          Arrays.asList("cudnn64_9.dll"),
          Arrays.asList("nvJitLink*.dll"),
          Arrays.asList("zlibwapi.dll", "libz.dll", "z.dll"));
  private static final List<List<String>> REQUIRED_NVIDIA_CUDA12_8_RUNTIME_DLL_GROUPS =
      Arrays.asList(
          Arrays.asList("cudart64_12.dll"),
          Arrays.asList("cublas64_12.dll"),
          Arrays.asList("cublasLt64_12.dll"),
          Arrays.asList("cudnn64_9.dll"),
          Arrays.asList("nvJitLink*.dll"),
          Arrays.asList("zlibwapi.dll", "libz.dll", "z.dll"));
  private static final List<List<String>> REQUIRED_NVIDIA_TRT10_9_RUNTIME_DLL_GROUPS =
      Arrays.asList(
          Arrays.asList("cudart64_12.dll"),
          Arrays.asList("cublas64_12.dll"),
          Arrays.asList("cublasLt64_12.dll"),
          Arrays.asList("cudnn64_9.dll"),
          Arrays.asList("nvJitLink*.dll"),
          Arrays.asList("nvinfer_10.dll", "nvinfer*.dll"),
          Arrays.asList("nvinfer_plugin_10.dll", "nvinfer_plugin*.dll"),
          Arrays.asList("zlibwapi.dll", "libz.dll", "z.dll"));
  private static final int MAX_APPLE_ANALYSIS_THREADS = 8;
  private static volatile boolean benchmarkEngineSyncSuppressed = false;
  private static final Object OPENCL_TUNING_CACHE_LOCK = new Object();

  private KataGoRuntimeHelper() {}

  private static boolean isWindowsPlatform() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return !osName.contains("darwin") && osName.contains("win");
  }

  public static final class NvidiaRuntimeStatus {
    public final boolean applicable;
    public final boolean ready;
    public final Path enginePath;
    public final Path runtimeDir;
    public final List<String> missingDlls;
    public final long downloadBytes;
    public final String detailText;

    private NvidiaRuntimeStatus(
        boolean applicable,
        boolean ready,
        Path enginePath,
        Path runtimeDir,
        List<String> missingDlls,
        long downloadBytes,
        String detailText) {
      this.applicable = applicable;
      this.ready = ready;
      this.enginePath = enginePath;
      this.runtimeDir = runtimeDir;
      this.missingDlls = missingDlls;
      this.downloadBytes = downloadBytes;
      this.detailText = detailText;
    }
  }

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

  public static boolean isBundledOpenClPath(Path enginePath) {
    if (!isWindowsPlatform()
        || enginePath == null
        || !Config.isBundledKataGoExecutable(enginePath)
        || resolveNvidiaBackend(enginePath) != null) {
      return false;
    }
    return OPENCL_BACKEND.equals(readEngineBackendMarker(enginePath));
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
    String backendLower = readEngineBackendMarker(enginePath);
    if (!backendLower.isEmpty()) {
      if (NVIDIA_TRT_BACKEND.equals(backendLower) || NVIDIA50_TRT_BACKEND.equals(backendLower)) {
        return NVIDIA_TRT_BACKEND;
      }
      if (NVIDIA50_CUDA_BACKEND.equals(backendLower)) {
        return NVIDIA50_CUDA_BACKEND;
      }
      if (NVIDIA_BACKEND.equals(backendLower)) {
        return NVIDIA_BACKEND;
      }
      if (backendLower.startsWith("nvidia")) {
        return backendLower;
      }
    }
    return null;
  }

  private static String readEngineBackendMarker(Path enginePath) {
    if (enginePath == null) {
      return "";
    }
    Path engineDir = enginePath.toAbsolutePath().normalize().getParent();
    if (engineDir == null) {
      return "";
    }
    Path markerPath = engineDir.resolve(ENGINE_BACKEND_MARKER_NAME);
    if (!Files.isRegularFile(markerPath)) {
      return "";
    }
    try {
      return Files.readString(markerPath, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
    } catch (IOException e) {
      return "";
    }
  }

  public static void ensureBundledRuntimeReady(Path enginePath, Window owner) throws IOException {
    NvidiaRuntimeStatus status = inspectNvidiaRuntime(enginePath);
    if (!status.applicable || status.ready) {
      return;
    }
    throw new IOException(buildMissingRuntimeMessage(status));
  }

  public static void configureBundledProcessBuilder(
      ProcessBuilder processBuilder, Path enginePath) {
    if (processBuilder == null || enginePath == null) {
      return;
    }
    if (!Config.isBundledKataGoExecutable(enginePath)) {
      return;
    }
    if (Lizzie.config != null) {
      processBuilder.directory(Lizzie.config.getRuntimeWorkDirectory());
    }
    Path engineDir = enginePath.getParent();
    if (engineDir == null) {
      return;
    }
    prependPath(processBuilder, engineDir);
    if (isWindowsPlatform() && isNvidiaBundledPath(enginePath)) {
      Path runtimeDir = getNvidiaRuntimeDir();
      if (Files.isDirectory(runtimeDir)) {
        prependPath(processBuilder, runtimeDir);
      }
      configureNvidiaRuntimeCacheEnvironment(processBuilder, enginePath, runtimeDir);
    }
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

    boolean openClFp32Compatibility = shouldUseOpenClFp32Compatibility(launchCommand, enginePath);
    Path homeDataDir =
        openClFp32Compatibility ? getOpenClFp32HomeDataDir() : getBundledHomeDataDir();
    if (homeDataDir == null) {
      return launchCommand;
    }
    try {
      Files.createDirectories(homeDataDir);
    } catch (IOException e) {
      e.printStackTrace();
      return launchCommand;
    }

    if (openClFp32Compatibility) {
      setOverrideConfig(launchCommand, "homeDataDir=" + homeDataDir.toString());
      setOverrideConfig(launchCommand, "openclUseFP16=false");
    } else {
      appendOverrideConfig(launchCommand, "homeDataDir=" + homeDataDir.toString());
      prepareBundledOpenClTuningCache(
          enginePath, resolveEffectiveHomeDataDir(launchCommand, homeDataDir));
    }
    appendAnalysisPvLenOverride(launchCommand);
    return launchCommand;
  }

  public static boolean isOpenClFp32CompatibilityActive(
      List<String> launchCommand, Path enginePath) {
    return isBundledOpenClPath(enginePath)
        && "false".equalsIgnoreCase(findOverrideConfigValue(launchCommand, "openclUseFP16"));
  }

  public static boolean shouldRecoverOpenClNativeExit(
      List<String> originalCommand,
      Path enginePath,
      int exitCode,
      boolean compatibilityAlreadyActive) {
    return !compatibilityAlreadyActive
        && exitCode == WINDOWS_FAST_FAIL_EXIT_CODE
        && isBundledOpenClPath(enginePath)
        && findCommandPath(originalCommand, "-model", "--model", "-weights", "--weights") != null;
  }

  public static boolean rememberOpenClFp32Compatibility(
      List<String> originalCommand, Path enginePath) {
    if (!isBundledOpenClPath(enginePath)) {
      return false;
    }
    Path marker = getOpenClFp32CompatibilityMarker();
    String signature =
        buildOpenClCompatibilitySignature(
            originalCommand, enginePath, resolveNvidiaDriverVersion());
    if (marker == null || signature.isEmpty()) {
      return false;
    }
    try {
      Files.createDirectories(marker.getParent());
      Files.writeString(
          marker,
          signature,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      return true;
    } catch (IOException e) {
      System.err.println(
          "Unable to remember KataGo OpenCL FP32 compatibility mode: " + e.getLocalizedMessage());
      return false;
    }
  }

  public static NvidiaRuntimeStatus inspectNvidiaRuntime(SetupSnapshot snapshot) {
    return inspectNvidiaRuntime(snapshot == null ? null : snapshot.enginePath);
  }

  public static NvidiaRuntimeStatus inspectNvidiaRuntime(Path enginePath) {
    Path runtimeDir = getNvidiaRuntimeDir();
    String backend = resolveNvidiaBackend(enginePath);
    if (!isWindowsPlatform() || backend == null) {
      return new NvidiaRuntimeStatus(
          false,
          false,
          enginePath,
          runtimeDir,
          new ArrayList<String>(),
          0L,
          resource(
              "AutoSetup.nvidiaRuntimeNotApplicable",
              "Current engine does not need the NVIDIA runtime."));
    }

    List<Path> searchDirs = collectRuntimeSearchDirs(enginePath, runtimeDir);
    List<List<String>> requiredDllGroups = requiredRuntimeDllGroups(enginePath, backend);
    List<String> missing = collectMissingRuntimeGroups(searchDirs, requiredDllGroups);
    Path readyDir = findDirectoryContainingRequiredDlls(searchDirs, requiredDllGroups);
    boolean ready = missing.isEmpty();
    String detailText;
    if (ready) {
      detailText =
          resource("AutoSetup.nvidiaRuntimeReady", "Ready")
              + "  |  "
              + (readyDir != null
                  ? readyDir.toAbsolutePath().normalize()
                  : formatRuntimeSearchDirs(searchDirs));
    } else {
      detailText =
          resource(
                  "AutoSetup.nvidiaRuntimeMissing",
                  "Bundled NVIDIA runtime files are missing. Please reinstall the NVIDIA package.")
              + "  |  "
              + String.join(", ", missing);
    }
    return new NvidiaRuntimeStatus(true, ready, enginePath, runtimeDir, missing, 0L, detailText);
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

  private static void prependPath(ProcessBuilder processBuilder, Path path) {
    if (processBuilder == null || path == null) {
      return;
    }
    String candidate = path.toAbsolutePath().normalize().toString();
    String separator = System.getProperty("path.separator", ";");
    String original = processBuilder.environment().get("PATH");
    LinkedHashSet<String> entries = new LinkedHashSet<String>();
    entries.add(candidate);
    if (original != null && !original.trim().isEmpty()) {
      entries.addAll(Arrays.asList(original.split(Pattern.quote(separator))));
    }
    StringBuilder rebuilt = new StringBuilder();
    for (String entry : entries) {
      String trimmed = entry == null ? "" : entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (rebuilt.length() > 0) {
        rebuilt.append(separator);
      }
      rebuilt.append(trimmed);
    }
    processBuilder.environment().put("PATH", rebuilt.toString());
  }

  private static void configureNvidiaRuntimeCacheEnvironment(
      ProcessBuilder processBuilder, Path enginePath, Path runtimeDir) {
    if (processBuilder == null || runtimeDir == null) {
      return;
    }
    String backend = resolveNvidiaBackend(enginePath);
    if (backend == null) {
      return;
    }
    try {
      Path cacheRoot = Files.createDirectories(runtimeDir.resolve(NVIDIA_RUNTIME_CACHE_DIR));
      Path cudaCache = Files.createDirectories(cacheRoot.resolve(NVIDIA_CUDA_CACHE_DIR));
      processBuilder.environment().put("CUDA_CACHE_PATH", cudaCache.toString());
      if (isTensorRtBackend(backend)) {
        Path tempCache = Files.createDirectories(cacheRoot.resolve(NVIDIA_TENSORRT_TEMP_DIR));
        processBuilder.environment().put("TEMP", tempCache.toString());
        processBuilder.environment().put("TMP", tempCache.toString());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
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

  private static Path getBundledHomeDataDir() {
    if (Lizzie.config == null) {
      return null;
    }
    return Lizzie.config
        .getRuntimeWorkDirectory()
        .toPath()
        .resolve(BUNDLED_HOME_DATA_DIR)
        .toAbsolutePath()
        .normalize();
  }

  private static Path getOpenClFp32HomeDataDir() {
    if (Lizzie.config == null) {
      return null;
    }
    return Lizzie.config
        .getRuntimeWorkDirectory()
        .toPath()
        .resolve(OPENCL_FP32_HOME_DATA_DIR)
        .toAbsolutePath()
        .normalize();
  }

  private static Path getOpenClFp32CompatibilityMarker() {
    Path homeDataDir = getOpenClFp32HomeDataDir();
    return homeDataDir == null ? null : homeDataDir.resolve(OPENCL_FP32_COMPATIBILITY_MARKER);
  }

  /**
   * Returns true when the bundled engine still needs a one-time OpenCL autotuning pass, i.e. no
   * cached tuning parameters exist yet. The first OpenCL tuning can take a few minutes, so callers
   * should grant a longer startup budget in that case.
   */
  public static boolean needsFirstOpenCLTuning(Path enginePath) {
    return needsFirstOpenCLTuning(enginePath, false);
  }

  public static boolean needsFirstOpenCLTuning(Path enginePath, boolean openClFp32Compatibility) {
    if (!isWindowsPlatform()) {
      return false;
    }
    if (enginePath == null || !Config.isBundledKataGoExecutable(enginePath)) {
      return false;
    }
    // NVIDIA TensorRT/CUDA packages do not rely on the OpenCL tuning cache.
    if (resolveNvidiaBackend(enginePath) != null) {
      return false;
    }
    Path homeDataDir =
        openClFp32Compatibility ? getOpenClFp32HomeDataDir() : getBundledHomeDataDir();
    if (homeDataDir == null) {
      return false;
    }
    Path tuningDir = homeDataDir.resolve("opencltuning");
    if (!Files.isDirectory(tuningDir)) {
      return true;
    }
    try (Stream<Path> entries = Files.list(tuningDir)) {
      return entries.noneMatch(
          p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"));
    } catch (IOException e) {
      return true;
    }
  }

  private static boolean shouldUseOpenClFp32Compatibility(List<String> command, Path enginePath) {
    if (!isBundledOpenClPath(enginePath)) {
      return false;
    }
    if ("false".equalsIgnoreCase(findOverrideConfigValue(command, "openclUseFP16"))) {
      return true;
    }
    String driverVersion = resolveNvidiaDriverVersion();
    Path marker = getOpenClFp32CompatibilityMarker();
    if (marker == null || !Files.isRegularFile(marker)) {
      return false;
    }
    String expected = buildOpenClCompatibilitySignature(command, enginePath, driverVersion);
    if (expected.isEmpty()) {
      return false;
    }
    try {
      return expected.equals(Files.readString(marker, StandardCharsets.UTF_8).trim());
    } catch (IOException e) {
      return false;
    }
  }

  private static String resolveNvidiaDriverVersion() {
    String configured = System.getProperty(OPENCL_NVIDIA_DRIVER_VERSION_PROPERTY, "").trim();
    if (!configured.isEmpty()) {
      return "none".equalsIgnoreCase(configured) ? "" : normalizeDriverVersion(configured);
    }
    return "";
  }

  private static String normalizeDriverVersion(String driverVersion) {
    return driverVersion == null ? "" : driverVersion.trim().replace(',', '.');
  }

  private static Path resolveEffectiveHomeDataDir(List<String> command, Path fallback) {
    String configured = findOverrideConfigValue(command, "homeDataDir");
    if (configured.isEmpty()) {
      return fallback;
    }
    try {
      Path path = Paths.get(configured);
      if (!path.isAbsolute() && Lizzie.config != null) {
        path = Lizzie.config.getRuntimeWorkDirectory().toPath().resolve(path);
      }
      return path.toAbsolutePath().normalize();
    } catch (Exception e) {
      return fallback;
    }
  }

  private static void prepareBundledOpenClTuningCache(Path enginePath, Path homeDataDir) {
    if (!isBundledOpenClPath(enginePath) || homeDataDir == null) {
      return;
    }
    synchronized (OPENCL_TUNING_CACHE_LOCK) {
      Path generationMarker = homeDataDir.resolve(OPENCL_TUNING_CACHE_GENERATION_MARKER);
      try {
        if (Files.isRegularFile(generationMarker)
            && OPENCL_TUNING_CACHE_GENERATION.equals(
                Files.readString(generationMarker, StandardCharsets.UTF_8).trim())) {
          return;
        }

        Path tuningDir = homeDataDir.resolve("opencltuning");
        if (Files.exists(tuningDir)) {
          Path quarantine = availableOpenClTuningQuarantinePath(homeDataDir);
          Files.move(tuningDir, quarantine);
          System.err.println(
              "Moved legacy KataGo OpenCL tuning cache to " + quarantine.toAbsolutePath());
        }
        Files.writeString(
            generationMarker,
            OPENCL_TUNING_CACHE_GENERATION,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
      } catch (IOException e) {
        System.err.println(
            "Unable to prepare KataGo OpenCL tuning cache: " + e.getLocalizedMessage());
      }
    }
  }

  private static Path availableOpenClTuningQuarantinePath(Path homeDataDir) {
    Path candidate = homeDataDir.resolve("opencltuning-legacy");
    int suffix = 1;
    while (Files.exists(candidate)) {
      candidate = homeDataDir.resolve("opencltuning-legacy-" + suffix++);
    }
    return candidate;
  }

  private static String buildOpenClCompatibilitySignature(
      List<String> command, Path enginePath, String driverVersion) {
    Path modelPath = findCommandPath(command, "-model", "--model", "-weights", "--weights");
    if (enginePath == null || modelPath == null) {
      return "";
    }
    StringBuilder signature = new StringBuilder("v1");
    signature.append("|driver=").append(normalizeDriverVersion(driverVersion));
    appendPathFingerprint(signature, enginePath);
    appendPathFingerprint(signature, modelPath);
    return signature.toString();
  }

  private static Path findCommandPath(List<String> command, String... options) {
    if (command == null || options == null) {
      return null;
    }
    for (int i = 0; i + 1 < command.size(); i++) {
      String candidate = command.get(i);
      for (String option : options) {
        if (!option.equals(candidate)) {
          continue;
        }
        try {
          return Paths.get(command.get(i + 1)).toAbsolutePath().normalize();
        } catch (Exception e) {
          return null;
        }
      }
    }
    return null;
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

  private static void appendPathFingerprint(StringBuilder builder, Path path) {
    if (path == null) {
      builder.append("|missing");
      return;
    }
    Path normalized = path.toAbsolutePath().normalize();
    builder.append('|').append(normalized);
    try {
      builder.append(':').append(Files.size(normalized));
      builder.append(':').append(Files.getLastModifiedTime(normalized).toMillis());
    } catch (IOException e) {
      builder.append(":0:0");
    }
  }

  private static List<Path> collectRuntimeSearchDirs(Path enginePath, Path runtimeDir) {
    LinkedHashSet<Path> paths = new LinkedHashSet<Path>();
    if (enginePath != null && enginePath.getParent() != null) {
      paths.add(enginePath.getParent().toAbsolutePath().normalize());
    }
    if (runtimeDir != null) {
      paths.add(runtimeDir.toAbsolutePath().normalize());
    }
    String pathEnv = System.getenv("PATH");
    if (pathEnv != null && !pathEnv.trim().isEmpty()) {
      String separator = System.getProperty("path.separator", ";");
      for (String entry : pathEnv.split(Pattern.quote(separator))) {
        if (entry == null || entry.trim().isEmpty()) {
          continue;
        }
        try {
          Path candidate = Paths.get(entry).toAbsolutePath().normalize();
          if (Files.isDirectory(candidate)) {
            paths.add(candidate);
          }
        } catch (Exception e) {
        }
      }
    }
    return new ArrayList<Path>(paths);
  }

  private static String formatRuntimeSearchDirs(List<Path> searchDirs) {
    if (searchDirs == null || searchDirs.isEmpty()) {
      return "";
    }
    List<String> displayPaths = new ArrayList<String>();
    for (Path dir : searchDirs) {
      if (dir == null) {
        continue;
      }
      displayPaths.add(dir.toAbsolutePath().normalize().toString());
      if (displayPaths.size() >= 2) {
        break;
      }
    }
    return String.join(" ; ", displayPaths);
  }

  private static boolean hasFile(List<Path> searchDirs, String fileName) {
    for (Path dir : searchDirs) {
      if (dir == null) {
        continue;
      }
      if (fileName.contains("*")) {
        String prefix = fileName.substring(0, fileName.indexOf('*'));
        String suffix = fileName.substring(fileName.indexOf('*') + 1);
        try (Stream<Path> files = Files.list(dir)) {
          boolean found =
              files.anyMatch(
                  path -> {
                    String name = path.getFileName().toString();
                    return Files.isRegularFile(path)
                        && name.startsWith(prefix)
                        && name.endsWith(suffix);
                  });
          if (found) {
            return true;
          }
        } catch (IOException e) {
        }
        continue;
      }
      if (Files.isRegularFile(dir.resolve(fileName))) {
        return true;
      }
    }
    return false;
  }

  private static List<List<String>> requiredRuntimeDllGroups(Path enginePath, String backend) {
    if (isTensorRtBackend(backend)) {
      return REQUIRED_NVIDIA_TRT10_9_RUNTIME_DLL_GROUPS;
    }
    if (NVIDIA50_CUDA_BACKEND.equalsIgnoreCase(backend)) {
      return REQUIRED_NVIDIA_CUDA12_8_RUNTIME_DLL_GROUPS;
    }
    if (usesLegacyCudnn8Runtime(enginePath)) {
      return REQUIRED_NVIDIA_CUDA12_1_CUDNN8_RUNTIME_DLL_GROUPS;
    }
    return REQUIRED_NVIDIA_CUDA12_1_CUDNN9_RUNTIME_DLL_GROUPS;
  }

  private static boolean usesLegacyCudnn8Runtime(Path enginePath) {
    if (enginePath == null || enginePath.getParent() == null) {
      return false;
    }
    Path engineDir = enginePath.getParent();
    Path manifest = engineDir.resolve("lizzieyzy-next-nvidia-runtime-manifest.txt");
    if (Files.isRegularFile(manifest)) {
      try {
        String text = Files.readString(manifest, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (text.contains("profile: cuda12.1-cudnn9")) {
          return false;
        }
        if (text.contains("profile: cuda12.1-cudnn8")) {
          return true;
        }
      } catch (IOException e) {
      }
    }
    return Files.isRegularFile(engineDir.resolve("cudnn64_8.dll"))
        && !Files.isRegularFile(engineDir.resolve("cudnn64_9.dll"));
  }

  private static List<String> collectMissingRuntimeGroups(
      List<Path> searchDirs, List<List<String>> requiredDllGroups) {
    List<String> missing = new ArrayList<String>();
    for (List<String> requirementGroup : requiredDllGroups) {
      if (!hasAnyFile(searchDirs, requirementGroup)) {
        missing.add(describeRequirementGroup(requirementGroup));
      }
    }
    return missing;
  }

  private static boolean hasAnyFile(List<Path> searchDirs, List<String> fileNames) {
    for (String fileName : fileNames) {
      if (hasFile(searchDirs, fileName)) {
        return true;
      }
    }
    return false;
  }

  private static String describeRequirementGroup(List<String> requirementGroup) {
    if (requirementGroup == null || requirementGroup.isEmpty()) {
      return "";
    }
    if (requirementGroup.size() == 1) {
      return requirementGroup.get(0);
    }
    return String.join(" or ", requirementGroup);
  }

  private static Path findDirectoryContainingRequiredDlls(
      List<Path> searchDirs, List<List<String>> requiredDllGroups) {
    for (Path dir : searchDirs) {
      if (dir == null) {
        continue;
      }
      boolean allPresent = true;
      for (List<String> requirementGroup : requiredDllGroups) {
        if (!hasAnyFile(Arrays.asList(dir), requirementGroup)) {
          allPresent = false;
          break;
        }
      }
      if (allPresent) {
        return dir.toAbsolutePath().normalize();
      }
    }
    return null;
  }

  private static String buildMissingRuntimeMessage(NvidiaRuntimeStatus status) {
    StringBuilder builder =
        new StringBuilder(
            status != null && isTensorRtBackend(resolveNvidiaBackend(status.enginePath))
                ? resource(
                    "AutoSetup.tensorRtRuntimeMissing",
                    "TensorRT runtime is not installed. Open KataGo Auto Setup and install TensorRT acceleration.")
                : resource(
                    "AutoSetup.nvidiaRuntimeInstallFailed",
                    "Bundled NVIDIA files are incomplete. Please reinstall the NVIDIA package."));
    if (status != null && status.missingDlls != null && !status.missingDlls.isEmpty()) {
      builder.append(" Missing: ").append(String.join(", ", status.missingDlls));
    }
    if (status != null && status.enginePath != null && status.enginePath.getParent() != null) {
      builder
          .append(" | ")
          .append(status.enginePath.getParent().toAbsolutePath().normalize().toString());
    }
    return builder.toString();
  }

  private static Path getNvidiaRuntimeDir() {
    if (Lizzie.config != null) {
      return Lizzie.config.getRuntimeWorkDirectory().toPath().resolve(NVIDIA_RUNTIME_ROOT);
    }
    return Paths.get(System.getProperty("user.dir", "."))
        .toAbsolutePath()
        .normalize()
        .resolve("runtime")
        .resolve(NVIDIA_RUNTIME_ROOT);
  }

  private static boolean isTensorRtBackend(String backend) {
    return NVIDIA_TRT_BACKEND.equalsIgnoreCase(backend)
        || NVIDIA50_TRT_BACKEND.equalsIgnoreCase(backend);
  }

  private static String resource(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.containsKey(key)) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (Exception e) {
    }
    return fallback;
  }
}
