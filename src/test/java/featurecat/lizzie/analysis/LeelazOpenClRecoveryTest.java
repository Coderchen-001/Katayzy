package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LeelazOpenClRecoveryTest {
  @Test
  void automaticRestartWaitsForTheFullStartupCommandSequence() {
    assertFalse(Leelaz.automaticRestartReady(false, false, true));
    assertFalse(Leelaz.automaticRestartReady(true, true, true));
    assertFalse(Leelaz.automaticRestartReady(true, false, false));
    assertTrue(Leelaz.automaticRestartReady(true, false, true));
  }

  @Test
  void currentOpenClNativeEofStartsAtMostOneAutomaticRecovery() throws Exception {
    Config previousConfig = Lizzie.config;
    String previousOsName = System.getProperty("os.name");
    String previousDriver = System.getProperty("lizzie.opencl.nvidiaDriverVersion");
    Path tempRoot = Files.createTempDirectory("leelaz-opencl-recovery");
    try {
      System.setProperty("os.name", "Windows 11");
      System.setProperty("lizzie.opencl.nvidiaDriverVersion", "566.36");
      Lizzie.config = ConfigTestHelper.createForTests(tempRoot.resolve("runtime-root"));
      Path enginePath = createOpenClEngine(tempRoot);
      Path modelPath = touch(tempRoot.resolve("weights/current.bin.gz"));
      RecordingRecoveryLeelaz engine = new RecordingRecoveryLeelaz();
      ExitedProcess process = new ExitedProcess((int) 0xC0000409L);
      setField(engine, "process", process);
      setField(
          engine,
          "inputStream",
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
      setField(
          engine,
          "commands",
          List.of(enginePath.toString(), "gtp", "-model", modelPath.toString()));
      engine.started = true;
      engine.isLoaded = true;

      invokeRead(engine);
      assertTrue(engine.recoveryStarted.await(2, TimeUnit.SECONDS));
      assertFalse(invokeOpenClRecovery(engine));
      assertEquals(1, process.destroyCount);
      assertFalse(engine.isStarted());
      assertEquals(1, engine.restartCount);
    } finally {
      restoreProperty("os.name", previousOsName);
      restoreProperty("lizzie.opencl.nvidiaDriverVersion", previousDriver);
      Lizzie.config = previousConfig;
    }
  }

  @Test
  void staleOpenClNativeEofDoesNotStartRecoveryOrDestroyReboundProcess() throws Exception {
    Config previousConfig = Lizzie.config;
    String previousOsName = System.getProperty("os.name");
    String previousDriver = System.getProperty("lizzie.opencl.nvidiaDriverVersion");
    Path tempRoot = Files.createTempDirectory("leelaz-stale-opencl-recovery");
    try {
      System.setProperty("os.name", "Windows 11");
      System.setProperty("lizzie.opencl.nvidiaDriverVersion", "566.36");
      Lizzie.config = ConfigTestHelper.createForTests(tempRoot.resolve("runtime-root"));
      Path enginePath = createOpenClEngine(tempRoot);
      Path modelPath = touch(tempRoot.resolve("weights/current.bin.gz"));
      RecordingRecoveryLeelaz engine = new RecordingRecoveryLeelaz();
      BlockingEofInputStream oldStdout = new BlockingEofInputStream();
      ExitedProcess oldProcess = new ExitedProcess((int) 0xC0000409L, oldStdout);
      setField(engine, "process", oldProcess);
      initializeStreams(engine, oldProcess);
      setField(
          engine,
          "commands",
          List.of(enginePath.toString(), "gtp", "-model", modelPath.toString()));
      engine.started = true;
      engine.isLoaded = true;

      AtomicReference<Throwable> readerFailure = new AtomicReference<>();
      Thread oldReader =
          new Thread(
              () -> {
                try {
                  invokeRead(engine);
                } catch (Throwable failure) {
                  readerFailure.set(failure);
                }
              },
              "stale-opencl-reader");
      oldReader.setDaemon(true);
      oldReader.start();
      assertTrue(oldStdout.awaitRead());

      ExitedProcess newProcess = new ExitedProcess(0);
      setField(engine, "process", newProcess);
      initializeStreams(engine, newProcess);
      oldStdout.release();
      oldReader.join(1000L);

      assertFalse(oldReader.isAlive());
      assertEquals(null, readerFailure.get());
      assertEquals(0, oldProcess.destroyCount);
      assertEquals(0, newProcess.destroyCount);
      assertEquals(0, engine.restartCount);
      assertTrue(engine.isStarted());
      assertTrue(engine.isLoaded());
    } finally {
      restoreProperty("os.name", previousOsName);
      restoreProperty("lizzie.opencl.nvidiaDriverVersion", previousDriver);
      Lizzie.config = previousConfig;
    }
  }

  private static Path createOpenClEngine(Path tempRoot) throws IOException {
    Path engineDirectory = Files.createDirectories(tempRoot.resolve("engines/katago/windows-x64"));
    Files.writeString(engineDirectory.resolve("lizzieyzy-next-engine-backend.txt"), "opencl");
    return touch(engineDirectory.resolve("katago.exe"));
  }

  private static Path touch(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    return Files.write(path, new byte[0]);
  }

  private static void invokeRead(Leelaz engine) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("read");
    method.setAccessible(true);
    method.invoke(engine);
  }

  private static boolean invokeOpenClRecovery(Leelaz engine) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("tryRecoverBundledOpenClNativeExit");
    method.setAccessible(true);
    return (Boolean) method.invoke(engine);
  }

  private static void initializeStreams(Leelaz engine, Process process) throws Exception {
    Method method =
        Leelaz.class.getDeclaredMethod(
            "initializeStreams", InputStream.class, OutputStream.class, InputStream.class);
    method.setAccessible(true);
    method.invoke(
        engine, process.getInputStream(), process.getOutputStream(), process.getErrorStream());
  }

  private static void setField(Leelaz engine, String name, Object value) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(engine, value);
  }

  private static void restoreProperty(String name, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, previousValue);
    }
  }

  private static final class RecordingRecoveryLeelaz extends Leelaz {
    private final CountDownLatch recoveryStarted = new CountDownLatch(1);
    private int restartCount;

    private RecordingRecoveryLeelaz() throws Exception {
      super("");
    }

    @Override
    void restartClosedEngine(int index, Runnable afterBoardRestore) {
      restartCount++;
      afterBoardRestore.run();
      recoveryStarted.countDown();
    }
  }

  private static final class ExitedProcess extends Process {
    private final int exitCode;
    private final InputStream stdout;
    private int destroyCount;

    private ExitedProcess(int exitCode) {
      this(exitCode, new ByteArrayInputStream(new byte[0]));
    }

    private ExitedProcess(int exitCode, InputStream stdout) {
      this.exitCode = exitCode;
      this.stdout = stdout;
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {
      destroyCount++;
    }
  }

  private static final class BlockingEofInputStream extends InputStream {
    private final CountDownLatch reading = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    @Override
    public int read() throws IOException {
      reading.countDown();
      try {
        if (!released.await(2, TimeUnit.SECONDS)) {
          throw new IOException("timed out waiting to release stale EOF");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IOException(interrupted);
      }
      return -1;
    }

    private boolean awaitRead() throws InterruptedException {
      return reading.await(1, TimeUnit.SECONDS);
    }

    private void release() {
      released.countDown();
    }
  }
}
