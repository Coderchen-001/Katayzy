package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class KataGoRuntimeHelperTest {
  private static final String OS_NAME_PROPERTY = "os.name";
  private static final String OS_ARCH_PROPERTY = "os.arch";
  private static final String PATH_SEPARATOR = System.getProperty("path.separator");
  private static final String WINDOWS_OS_NAME = "Windows 11";

  @Test
  void externalEngineKeepsOriginalDirectory() throws Exception {
    Path tempRoot = Files.createTempDirectory("katago-helper-external");
    Path enginePath = touch(tempRoot.resolve("external-engine").resolve("katago.exe"));
    Path originalDirectory = Files.createDirectories(tempRoot.resolve("working-dir"));
    Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
    String originalPath = String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta"));
    ProcessBuilder processBuilder = createProcessBuilder(originalDirectory, originalPath);

    withConfig(
        runtimeWorkDirectory,
        () -> KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath));

    assertEquals(
        normalize(originalDirectory),
        normalize(processBuilder.directory().toPath()),
        "External engine should keep its directory.");
    assertEquals(
        originalPath,
        processBuilder.environment().get("PATH"),
        "External engine should keep PATH unchanged.");
  }

  @Test
  void bundledOpenclEngineUsesRuntimeDirectory() throws Exception {
    Path tempRoot = Files.createTempDirectory("katago-helper-bundled-opencl");
    Path enginePath =
        touch(
            tempRoot
                .resolve("engines")
                .resolve("katago")
                .resolve("windows-x64-opencl")
                .resolve("katago.exe"));
    Path originalDirectory = Files.createDirectories(tempRoot.resolve("working-dir"));
    Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
    String originalPath = String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta"));
    ProcessBuilder processBuilder = createProcessBuilder(originalDirectory, originalPath);

    withConfig(
        runtimeWorkDirectory,
        () -> KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath));

    assertEquals(
        normalize(runtimeWorkDirectory),
        normalize(processBuilder.directory().toPath()),
        "Bundled OpenCL engine should use runtime directory.");
    assertEquals(
        normalize(enginePath.getParent()),
        firstPathEntry(processBuilder.environment().get("PATH")),
        "Bundled OpenCL engine should prepend its engine directory.");
  }

  @Test
  void bundledEngineUnderSpacedUnicodePathKeepsRuntimeStateOutOfEngineDirectory() throws Exception {
    Path tempRoot = Files.createTempDirectory("katago-helper-spaced-path");
    Path portableRoot = Files.createDirectories(tempRoot.resolve("LizzieYzy Next 测试 portable"));
    Path enginePath =
        touch(
            portableRoot
                .resolve("app")
                .resolve("engines")
                .resolve("katago")
                .resolve("windows-x64")
                .resolve("katago.exe"));
    Path originalDirectory = Files.createDirectories(enginePath.getParent());
    Path runtimeWorkDirectory =
        Files.createDirectories(portableRoot.resolve("user-data").resolve("runtime"));
    ProcessBuilder processBuilder =
        createProcessBuilder(originalDirectory, String.join(PATH_SEPARATOR, "alpha", "beta"));

    withConfig(
        runtimeWorkDirectory,
        () -> {
          List<String> launchCommand =
              KataGoRuntimeHelper.prepareBundledLaunchCommand(
                  Arrays.asList(enginePath.toString(), "gtp", "-config", "gtp.cfg"), enginePath);
          KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath);

          assertEquals(
              normalize(runtimeWorkDirectory),
              normalize(processBuilder.directory().toPath()),
              "A portable path containing spaces must still use user-data/runtime.");
          assertEquals(
              normalize(enginePath.getParent()),
              firstPathEntry(processBuilder.environment().get("PATH")));
          int overrideIndex = launchCommand.indexOf("-override-config");
          assertTrue(overrideIndex >= 0);
          String overrides = launchCommand.get(overrideIndex + 1);
          assertTrue(
              overrides.contains(
                  "homeDataDir="
                      + runtimeWorkDirectory.resolve("katago-home").toAbsolutePath().normalize()),
              "KataGo homeDataDir must remain one structured argument even when it has spaces.");
          assertFalse(
              Files.exists(enginePath.getParent().resolve("KataGoData")),
              "The immutable engine directory must not receive cache data.");
        });
  }

  @Test
  void bundledOpenclEngineNeedsFirstTuningUntilCacheExists() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-opencl-tuning");
          Path enginePath =
              touch(
                  tempRoot
                      .resolve("engines")
                      .resolve("katago")
                      .resolve("windows-x64-opencl")
                      .resolve("katago.exe"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                assertTrue(
                    KataGoRuntimeHelper.needsFirstOpenCLTuning(enginePath),
                    "Bundled OpenCL should get the longer startup budget before tuning exists.");

                Path tuningDir =
                    Files.createDirectories(
                        runtimeWorkDirectory.resolve("katago-home/opencltuning"));
                touch(tuningDir.resolve("tune11_gpu0.txt"));

                assertFalse(
                    KataGoRuntimeHelper.needsFirstOpenCLTuning(enginePath),
                    "Existing OpenCL tuning cache should restore the normal startup timeout.");
              });
        });
  }

  @Test
  void bundledNvidiaEngineDoesNotNeedOpenclTuningBudget() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia-no-opencl-tuning");
          Path enginePath =
              touch(
                  tempRoot
                      .resolve("engines")
                      .resolve("katago")
                      .resolve("windows-x64-nvidia")
                      .resolve("katago.exe"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () ->
                  assertFalse(
                      KataGoRuntimeHelper.needsFirstOpenCLTuning(enginePath),
                      "Bundled NVIDIA engines should not use the OpenCL tuning watchdog budget."));
        });
  }

  @Test
  void bundledNvidiaEnginePrependsRuntimePath() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-bundled-nvidia");
          Path enginePath =
              touch(
                  tempRoot
                      .resolve("engines")
                      .resolve("katago")
                      .resolve("windows-x64-nvidia")
                      .resolve("katago.exe"));
          Path originalDirectory = Files.createDirectories(tempRoot.resolve("working-dir"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          Path runtimeDir = Files.createDirectories(runtimeWorkDirectory.resolve("nvidia-runtime"));
          String originalPath = String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta"));
          ProcessBuilder processBuilder = createProcessBuilder(originalDirectory, originalPath);

          withConfig(
              runtimeWorkDirectory,
              () -> KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath));

          assertEquals(
              normalize(runtimeWorkDirectory),
              normalize(processBuilder.directory().toPath()),
              "Bundled NVIDIA engine should use runtime directory.");
          assertEquals(
              normalize(runtimeDir),
              firstPathEntry(processBuilder.environment().get("PATH")),
              "Bundled NVIDIA engine should prepend runtime directory first.");
          assertEquals(
              normalize(enginePath.getParent()),
              secondPathEntry(processBuilder.environment().get("PATH")),
              "Bundled NVIDIA engine should keep engine directory after runtime directory.");
        });
  }

  @Test
  void nvidia50MarkerPrependsRuntimePath() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-bundled-nvidia50");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia50-cuda");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          Path originalDirectory = Files.createDirectories(tempRoot.resolve("working-dir"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          Path runtimeDir = Files.createDirectories(runtimeWorkDirectory.resolve("nvidia-runtime"));
          String originalPath = String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta"));
          ProcessBuilder processBuilder = createProcessBuilder(originalDirectory, originalPath);

          withConfig(
              runtimeWorkDirectory,
              () -> KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath));

          assertEquals(
              normalize(runtimeDir),
              firstPathEntry(processBuilder.environment().get("PATH")),
              "RTX 50 NVIDIA package should prepend the runtime directory.");
          assertEquals(
              normalize(enginePath.getParent()),
              secondPathEntry(processBuilder.environment().get("PATH")),
              "RTX 50 NVIDIA package should keep the engine directory after runtime.");
        });
  }

  @Test
  void tensorRtLaunchKeepsCudaAndTempCachesInsideRuntimeDirectory() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-tensorrt-cache-env");
          Path enginePath =
              touch(
                  tempRoot
                      .resolve("engines")
                      .resolve("katago")
                      .resolve("windows-x64-nvidia-tensorrt")
                      .resolve("katago.exe"));
          Path originalDirectory = Files.createDirectories(tempRoot.resolve("working-dir"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          Path runtimeDir = Files.createDirectories(runtimeWorkDirectory.resolve("nvidia-runtime"));
          String originalPath = String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta"));
          ProcessBuilder processBuilder = createProcessBuilder(originalDirectory, originalPath);

          withConfig(
              runtimeWorkDirectory,
              () -> KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath));

          Path expectedCudaCache = runtimeDir.resolve("cache").resolve("cuda");
          Path expectedTempCache = runtimeDir.resolve("cache").resolve("temp");
          assertEquals(
              normalize(expectedCudaCache),
              normalize(Path.of(processBuilder.environment().get("CUDA_CACHE_PATH"))),
              "Bundled TensorRT should keep CUDA cache under the app runtime directory.");
          assertEquals(
              normalize(expectedTempCache),
              normalize(Path.of(processBuilder.environment().get("TEMP"))),
              "Bundled TensorRT should keep temp files under the app runtime directory.");
          assertEquals(
              normalize(expectedTempCache),
              normalize(Path.of(processBuilder.environment().get("TMP"))),
              "Bundled TensorRT should keep temp files under the app runtime directory.");
          assertTrue(Files.isDirectory(expectedCudaCache));
          assertTrue(Files.isDirectory(expectedTempCache));
        });
  }

  @Test
  void tensorRtUnderSpacedPortablePathUsesSeparateRuntimeDirectory() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-separated-tensorrt");
          Path portableRoot =
              Files.createDirectories(tempRoot.resolve("LizzieYzy Next CUDA portable"));
          Path runtimeWorkDirectory =
              Files.createDirectories(portableRoot.resolve("user-data").resolve("runtime"));
          Path engineDir =
              Files.createDirectories(
                  runtimeWorkDirectory
                      .resolve("engines")
                      .resolve("katago")
                      .resolve("windows-x64-nvidia-tensorrt"));
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia-tensorrt");
          Path runtimeDir = Files.createDirectories(runtimeWorkDirectory.resolve("nvidia-runtime"));
          touchRequiredCuda12_8Dlls(runtimeDir);
          touch(runtimeDir.resolve("nvinfer_10.dll"));
          touch(runtimeDir.resolve("nvinfer_plugin_10.dll"));
          Path originalDirectory = Files.createDirectories(portableRoot.resolve("app"));
          ProcessBuilder processBuilder =
              createProcessBuilder(
                  originalDirectory, String.join(PATH_SEPARATOR, Arrays.asList("alpha", "beta")));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);
                KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, enginePath);

                assertTrue(
                    status.ready,
                    "TensorRT should be ready when its engine and runtime are stored separately.");
                assertEquals(
                    normalize(runtimeWorkDirectory),
                    normalize(processBuilder.directory().toPath()),
                    "The TensorRT process should keep all mutable state in user-data/runtime.");
                assertEquals(
                    normalize(runtimeDir),
                    firstPathEntry(processBuilder.environment().get("PATH")),
                    "The separately installed NVIDIA runtime must be first on PATH.");
                assertEquals(
                    normalize(engineDir),
                    secondPathEntry(processBuilder.environment().get("PATH")),
                    "The TensorRT engine directory should follow its runtime on PATH.");
                assertFalse(
                    Files.isRegularFile(engineDir.resolve("cudnn64_9.dll")),
                    "Runtime DLLs should not need to be duplicated into the engine directory.");
              });
        });
  }

  @Test
  void standardNvidia117RuntimeRequiresCudnn9() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia117-runtime");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia");
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-nvidia-runtime-manifest.txt"),
              "Profile: cuda12.1-cudnn9\n");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchCommonCuda12Dlls(engineDir);
          touch(engineDir.resolve("cudnn64_8.dll"));
          touch(engineDir.resolve("libz.dll"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable);
                assertFalse(status.ready);
                assertTrue(status.missingDlls.contains("cudnn64_9.dll"));
              });
        });
  }

  @Test
  void standardNvidia117RuntimeAcceptsOfficialZDllName() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia117-zdll");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia");
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-nvidia-runtime-manifest.txt"),
              "Profile: cuda12.1-cudnn9\n");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchCommonCuda12Dlls(engineDir);
          touch(engineDir.resolve("cudnn64_9.dll"));
          touch(engineDir.resolve("z.dll"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable);
                assertTrue(status.ready, "KataGo 1.17's official z.dll must satisfy the runtime check.");
                assertTrue(status.missingDlls.isEmpty());
              });
        });
  }

  @Test
  void legacyStandardNvidiaRuntimeStillAcceptsCudnn8() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia-legacy-runtime");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia");
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-nvidia-runtime-manifest.txt"),
              "Profile: cuda12.1-cudnn8\n");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchCommonCuda12Dlls(engineDir);
          touch(engineDir.resolve("cudnn64_8.dll"));
          touch(engineDir.resolve("libz.dll"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable);
                assertTrue(status.ready);
                assertTrue(status.missingDlls.isEmpty());
              });
        });
  }

  @Test
  void nvidia50CudaRuntimeAcceptsCudnn9() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia50-cuda-runtime");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia50-cuda");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchRequiredCuda12_8Dlls(engineDir);
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable, "RTX 50 CUDA package should need NVIDIA runtime.");
                assertTrue(status.ready, "CUDA 12.8/cuDNN 9 runtime should satisfy RTX 50 CUDA.");
                assertEquals(0, status.missingDlls.size());
              });
        });
  }

  @Test
  void nvidia50CudaRuntimeRejectsOldCudnn8OnlyBundle() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia50-cuda-old-cudnn");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(
              engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia50-cuda");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchCommonCuda12Dlls(engineDir);
          touch(engineDir.resolve("cudnn64_8.dll"));
          touch(engineDir.resolve("libz.dll"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable);
                assertEquals(false, status.ready);
                assertTrue(
                    status.missingDlls.contains("cudnn64_9.dll"),
                    "RTX 50 CUDA package must require cuDNN 9.");
              });
        });
  }

  @Test
  void nvidia50TensorRtRuntimeRequiresTensorRtDlls() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-nvidia50-trt-runtime");
          Path engineDir =
              Files.createDirectories(
                  tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
          Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia50-trt");
          Path enginePath = touch(engineDir.resolve("katago.exe"));
          touchRequiredCuda12_8Dlls(engineDir);
          touch(engineDir.resolve("nvinfer_10.dll"));
          touch(engineDir.resolve("nvinfer_plugin_10.dll"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));

          withConfig(
              runtimeWorkDirectory,
              () -> {
                KataGoRuntimeHelper.NvidiaRuntimeStatus status =
                    KataGoRuntimeHelper.inspectNvidiaRuntime(enginePath);

                assertTrue(status.applicable, "TensorRT package should need NVIDIA runtime.");
                assertTrue(status.ready, "TensorRT runtime DLLs should satisfy the package.");
              });
        });
  }

  @Test
  void bundledLaunchCommandAddsHomeDataDirAndPvLengthOverride() throws Exception {
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
          assertTrue(overrides.contains("homeDataDir="));
          assertTrue(overrides.contains("analysisPVLen=32"));
        });
  }

  @Test
  void legacyOpenClTuningCacheIsQuarantinedBeforeFreshFp16Tuning() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-opencl-tuning-generation");
          Path enginePath = createOpenClEngine(tempRoot);
          Path modelPath = touch(tempRoot.resolve("weights").resolve("current.bin.gz"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          Path homeDataDir = runtimeWorkDirectory.resolve("katago-home");
          Path legacyTuning =
              Files.createDirectories(homeDataDir.resolve("opencltuning"))
                  .resolve("tune11_gpuNVIDIA_x19_y19_c512_mv15.txt");
          Files.writeString(legacyTuning, "unsafe concurrent tuning");
          String previousDriver = System.getProperty("lizzie.opencl.nvidiaDriverVersion");
          try {
            System.setProperty("lizzie.opencl.nvidiaDriverVersion", "560.76");
            withConfig(
                runtimeWorkDirectory,
                () -> {
                  List<String> command =
                      KataGoRuntimeHelper.prepareBundledLaunchCommand(
                          Arrays.asList(
                              enginePath.toString(),
                              "gtp",
                              "-model",
                              modelPath.toString(),
                              "-config",
                              "gtp.cfg",
                              "-override-config",
                              "numSearchThreads=2"),
                          enginePath);

                  String overrides = command.get(command.indexOf("-override-config") + 1);
                  assertTrue(
                      overrides.contains(
                          "homeDataDir=" + homeDataDir.toAbsolutePath().normalize()));
                  assertTrue(overrides.contains("numSearchThreads=2"));
                  assertFalse(overrides.contains("openclUseFP16=false"));
                  assertFalse(
                      KataGoRuntimeHelper.isOpenClFp32CompatibilityActive(command, enginePath));
                  assertFalse(Files.exists(legacyTuning));
                  Path quarantine = homeDataDir.resolve("opencltuning-legacy");
                  assertEquals(
                      "unsafe concurrent tuning",
                      Files.readString(quarantine.resolve(legacyTuning.getFileName())));
                  assertEquals(
                      "serialized-launch-v1",
                      Files.readString(homeDataDir.resolve("lizzie-opencl-tuning-generation.txt")));
                  assertTrue(KataGoRuntimeHelper.needsFirstOpenCLTuning(enginePath));

                  Path freshTuning =
                      Files.createDirectories(homeDataDir.resolve("opencltuning"))
                          .resolve("fresh.txt");
                  Files.writeString(freshTuning, "fresh serialized tuning");
                  KataGoRuntimeHelper.prepareBundledLaunchCommand(
                      Arrays.asList(
                          enginePath.toString(),
                          "gtp",
                          "-model",
                          modelPath.toString(),
                          "-config",
                          "gtp.cfg"),
                      enginePath);
                  assertEquals("fresh serialized tuning", Files.readString(freshTuning));
                });
          } finally {
            restoreProperty("lizzie.opencl.nvidiaDriverVersion", previousDriver);
          }
        });
  }

  @Test
  void currentNvidiaOpenClDriverKeepsNormalFp16PathWithoutFailureMarker() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-opencl-current-driver");
          Path enginePath = createOpenClEngine(tempRoot);
          Path modelPath = touch(tempRoot.resolve("weights").resolve("current.bin.gz"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          String previousDriver = System.getProperty("lizzie.opencl.nvidiaDriverVersion");
          try {
            System.setProperty("lizzie.opencl.nvidiaDriverVersion", "610.74");
            withConfig(
                runtimeWorkDirectory,
                () -> {
                  List<String> command =
                      KataGoRuntimeHelper.prepareBundledLaunchCommand(
                          Arrays.asList(
                              enginePath.toString(),
                              "gtp",
                              "-model",
                              modelPath.toString(),
                              "-config",
                              "gtp.cfg"),
                          enginePath);

                  String overrides = command.get(command.indexOf("-override-config") + 1);
                  assertTrue(
                      overrides.contains(
                          "homeDataDir="
                              + runtimeWorkDirectory
                                  .resolve("katago-home")
                                  .toAbsolutePath()
                                  .normalize()));
                  assertFalse(overrides.contains("openclUseFP16=false"));
                  assertFalse(
                      KataGoRuntimeHelper.isOpenClFp32CompatibilityActive(command, enginePath));
                });
          } finally {
            restoreProperty("lizzie.opencl.nvidiaDriverVersion", previousDriver);
          }
        });
  }

  @Test
  void explicitOpenClFp32OverrideUsesItsOwnTuningCache() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-opencl-explicit-fp32");
          Path enginePath = createOpenClEngine(tempRoot);
          Path modelPath = touch(tempRoot.resolve("weights").resolve("current.bin.gz"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          withConfig(
              runtimeWorkDirectory,
              () -> {
                List<String> command =
                    KataGoRuntimeHelper.prepareBundledLaunchCommand(
                        Arrays.asList(
                            enginePath.toString(),
                            "gtp",
                            "-model",
                            modelPath.toString(),
                            "-config",
                            "gtp.cfg",
                            "-override-config",
                            "homeDataDir=stale-fp16-cache,openclUseFP16=false"),
                        enginePath);

                String overrides = command.get(command.indexOf("-override-config") + 1);
                assertTrue(overrides.contains("openclUseFP16=false"));
                assertFalse(overrides.contains("homeDataDir=stale-fp16-cache"));
                assertTrue(
                    overrides.contains(
                        "homeDataDir="
                            + runtimeWorkDirectory
                                .resolve("katago-home-opencl-fp32")
                                .toAbsolutePath()
                                .normalize()));
                assertTrue(
                    KataGoRuntimeHelper.isOpenClFp32CompatibilityActive(command, enginePath));
              });
        });
  }

  @Test
  void nativeOpenClFastFailIsRememberedOnlyForMatchingDriverEngineAndModel() throws Exception {
    withOsName(
        WINDOWS_OS_NAME,
        () -> {
          Path tempRoot = Files.createTempDirectory("katago-helper-opencl-learned-fallback");
          Path enginePath = createOpenClEngine(tempRoot);
          Path modelPath = touch(tempRoot.resolve("weights").resolve("current.bin.gz"));
          Path runtimeWorkDirectory = Files.createDirectories(tempRoot.resolve("runtime-root"));
          List<String> originalCommand =
              Arrays.asList(
                  enginePath.toString(),
                  "gtp",
                  "-model",
                  modelPath.toString(),
                  "-config",
                  "gtp.cfg");
          String previousDriver = System.getProperty("lizzie.opencl.nvidiaDriverVersion");
          try {
            System.setProperty("lizzie.opencl.nvidiaDriverVersion", "566.36");
            withConfig(
                runtimeWorkDirectory,
                () -> {
                  assertFalse(
                      KataGoRuntimeHelper.shouldRecoverOpenClNativeExit(
                          originalCommand, enginePath, 1, false));
                  assertTrue(
                      KataGoRuntimeHelper.shouldRecoverOpenClNativeExit(
                          originalCommand, enginePath, (int) 0xC0000409L, false));
                  assertFalse(
                      KataGoRuntimeHelper.shouldRecoverOpenClNativeExit(
                          originalCommand, enginePath, (int) 0xC0000409L, true));
                  assertTrue(
                      KataGoRuntimeHelper.rememberOpenClFp32Compatibility(
                          originalCommand, enginePath));

                  List<String> recovered =
                      KataGoRuntimeHelper.prepareBundledLaunchCommand(originalCommand, enginePath);
                  assertTrue(
                      KataGoRuntimeHelper.isOpenClFp32CompatibilityActive(recovered, enginePath));

                  Files.write(modelPath, new byte[] {1, 2, 3});
                  List<String> changedModel =
                      KataGoRuntimeHelper.prepareBundledLaunchCommand(originalCommand, enginePath);
                  assertFalse(
                      KataGoRuntimeHelper.isOpenClFp32CompatibilityActive(
                          changedModel, enginePath));
                });
          } finally {
            restoreProperty("lizzie.opencl.nvidiaDriverVersion", previousDriver);
          }
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

  private static ProcessBuilder createProcessBuilder(Path directory, String pathValue) {
    ProcessBuilder processBuilder = new ProcessBuilder("echo");
    processBuilder.directory(directory.toFile());
    processBuilder.environment().put("PATH", pathValue);
    return processBuilder;
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

  private static void withOsName(String osName, ThrowingRunnable action) throws Exception {
    String previousOsName = System.getProperty(OS_NAME_PROPERTY);
    try {
      System.setProperty(OS_NAME_PROPERTY, osName);
      action.run();
    } finally {
      restoreOsName(previousOsName);
    }
  }


  private static void restoreOsName(String previousOsName) {
    if (previousOsName == null) {
      System.clearProperty(OS_NAME_PROPERTY);
      return;
    }
    System.setProperty(OS_NAME_PROPERTY, previousOsName);
  }

  private static Path firstPathEntry(String pathValue) {
    return Path.of(pathValue.split(java.util.regex.Pattern.quote(PATH_SEPARATOR))[0])
        .toAbsolutePath()
        .normalize();
  }

  private static Path secondPathEntry(String pathValue) {
    return Path.of(pathValue.split(java.util.regex.Pattern.quote(PATH_SEPARATOR))[1])
        .toAbsolutePath()
        .normalize();
  }

  private static Path touch(Path file) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.write(file, new byte[0]);
  }

  private static Path createOpenClEngine(Path tempRoot) throws IOException {
    Path engineDir =
        Files.createDirectories(
            tempRoot.resolve("engines").resolve("katago").resolve("windows-x64"));
    Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "opencl");
    return touch(engineDir.resolve("katago.exe"));
  }

  private static void touchCommonCuda12Dlls(Path directory) throws IOException {
    touch(directory.resolve("cudart64_12.dll"));
    touch(directory.resolve("cublas64_12.dll"));
    touch(directory.resolve("cublasLt64_12.dll"));
    touch(directory.resolve("nvJitLink64_12.dll"));
  }

  private static void touchRequiredCuda12_8Dlls(Path directory) throws IOException {
    touchCommonCuda12Dlls(directory);
    touch(directory.resolve("cudnn64_9.dll"));
    touch(directory.resolve("z.dll"));
  }

  private static void restoreProperty(String key, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(key);
      return;
    }
    System.setProperty(key, previousValue);
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
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
