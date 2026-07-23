package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.GtpConsolePane;
import featurecat.lizzie.rules.Board;
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
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LeelazReaderIncarnationTest {
  @Test
  void staleReadersDoNotConsumeOrTerminateReboundStreams() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      RecordingProcess oldProcess = new RecordingProcess();
      BlockingInputStream oldStdout = new BlockingInputStream("\n");
      BlockingInputStream oldStderr = new BlockingInputStream("", true);
      setField(engine, "process", oldProcess);
      initializeStreams(engine, oldStdout, oldStderr);
      Object oldBinding = getField(engine, "readerStreamBinding");
      engine.started = true;
      engine.isLoaded = true;
      engine.isNormalEnd = true;

      AtomicReference<Throwable> stdoutFailure = new AtomicReference<>();
      AtomicReference<Throwable> stderrFailure = new AtomicReference<>();
      Thread stdoutThread = invokeInThread(engine, "read", stdoutFailure);
      Thread stderrThread = invokeInThread(engine, "readError", stderrFailure);
      assertTrue(oldStdout.awaitRead());
      assertTrue(oldStderr.awaitRead());

      RecordingProcess newProcess = new RecordingProcess();
      ByteArrayInputStream newStdout =
          new ByteArrayInputStream("new-stdout\n".getBytes(StandardCharsets.UTF_8));
      ByteArrayInputStream newStderr =
          new ByteArrayInputStream("new-stderr\n".getBytes(StandardCharsets.UTF_8));
      setField(engine, "process", newProcess);
      initializeStreams(engine, newStdout, newStderr);
      Lizzie.leelaz = engine;
      engine.isKatago = true;
      engine.commandLists.addAll(
          java.util.List.of(
              "stop",
              "boardsize",
              "komi",
              "kata-get-rules",
              "kata-set-rules",
              "clear_board",
              "play",
              "set_position",
              "kata-analyze"));
      setField(engine, "endGetCommandList", true);
      assertEquals(
          Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE,
          engine.beginForegroundAnalysisLease(new Object(), line -> {}, () -> {}, () -> {}));
      invokeTerminal(engine, oldBinding);

      oldStdout.release();
      oldStderr.release();
      stdoutThread.join(1000L);
      stderrThread.join(1000L);

      assertFalse(stdoutThread.isAlive());
      assertFalse(stderrThread.isAlive());
      assertEquals(null, stdoutFailure.get());
      assertEquals(null, stderrFailure.get());
      assertEquals(0, newProcess.destroyCount);
      assertTrue(engine.isStarted());
      assertTrue(engine.isLoaded());
      assertTrue(engine.hasExclusiveGtpLease());
      assertEquals("new-stdout", currentReader(engine, "inputStream").readLine());
      assertEquals("new-stderr", currentReader(engine, "errorStream").readLine());
      assertFalse(recentLines(engine, "recentStdoutLines").contains(""));
      engine.endExclusiveGtpSession();
    }
  }

  @Test
  void currentStdoutTerminalWaitsForInProgressStderrLine() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      RecordingProcess process = new RecordingProcess();
      BlockingGtpConsole console = allocate(BlockingGtpConsole.class);
      console.initialize();
      Lizzie.gtpConsole = console;
      setField(engine, "process", process);
      initializeStreams(engine, bytes(""), bytes("held-stderr-line\n"));
      Lizzie.leelaz = engine;
      engine.started = true;
      engine.isLoaded = true;
      engine.isNormalEnd = true;

      AtomicReference<Throwable> stderrFailure = new AtomicReference<>();
      Thread stderrThread = invokeInThread(engine, "readError", stderrFailure);
      assertTrue(console.awaitLine());

      AtomicReference<Throwable> stdoutFailure = new AtomicReference<>();
      Thread stdoutThread = invokeInThread(engine, "read", stdoutFailure);
      stdoutThread.join(1000L);

      assertFalse(stdoutThread.isAlive());
      assertEquals(null, stdoutFailure.get());
      assertEquals(0, process.destroyCount);
      assertTrue(engine.isStarted());

      console.releaseLine();
      stderrThread.join(1000L);

      assertFalse(stderrThread.isAlive());
      assertEquals(null, stderrFailure.get());
      assertEquals(1, process.destroyCount);
      assertFalse(engine.isStarted());
    }
  }

  @Test
  void transportShutdownFailureStillCompletesTerminalCleanupOnce() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      CleanupRecordingLeelaz engine = new CleanupRecordingLeelaz();
      RecordingProcess process = new ThrowingProcess();
      BlockingGtpConsole console = allocate(BlockingGtpConsole.class);
      console.initialize();
      Lizzie.gtpConsole = console;
      setField(engine, "process", process);
      initializeStreams(engine, bytes("held-stdout-line\n"), bytes(""));
      Object binding = getField(engine, "readerStreamBinding");
      Lizzie.leelaz = engine;
      engine.started = true;
      engine.isLoaded = true;
      engine.isNormalEnd = true;
      engine.isKatago = true;
      engine.commandLists.addAll(
          java.util.List.of(
              "stop",
              "boardsize",
              "komi",
              "kata-get-rules",
              "kata-set-rules",
              "clear_board",
              "play",
              "set_position",
              "kata-analyze"));
      setField(engine, "endGetCommandList", true);

      AtomicReference<Throwable> stdoutFailure = new AtomicReference<>();
      Thread stdoutThread = invokeInThread(engine, "read", stdoutFailure);
      assertTrue(console.awaitLine());
      assertEquals(
          Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE,
          engine.beginForegroundAnalysisLease(new Object(), line -> {}, () -> {}, () -> {}));
      assertTrue(engine.hasExclusiveGtpLease());
      invokeTerminal(engine, binding);

      console.releaseLine();
      stdoutThread.join(1000L);

      assertFalse(stdoutThread.isAlive());
      assertEquals(null, stdoutFailure.get());
      assertEquals(0, readerLinesInProgress(binding));
      assertFalse(engine.isStarted());
      assertFalse(engine.hasExclusiveGtpLease());
      assertEquals(1, engine.readBoardCleanupCount);
      assertEquals(1, process.destroyCount);

      invokeTerminal(engine, binding);
      assertEquals(1, engine.readBoardCleanupCount);
      assertEquals(1, process.destroyCount);
    }
  }

  @Test
  void parserTriggeredTerminalCleansUpOnceAfterCurrentLineIsReleased() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      RecordingProcess process = new RecordingProcess();
      setField(engine, "process", process);
      initializeStreams(engine, bytes(""), bytes("info parser-triggered shutdown\n"));
      Object binding = getField(engine, "readerStreamBinding");
      process.observeReaderLines(engine);
      Lizzie.leelaz = engine;
      engine.started = true;
      engine.isLoaded = true;
      engine.isNormalEnd = true;
      engine.isZen = true;
      EngineManager.isEngineGame = true;
      EngineManager.engineGameInfo = new EngineGameInfo();

      Method readError = Leelaz.class.getDeclaredMethod("readError");
      readError.setAccessible(true);
      readError.invoke(engine);

      assertEquals(1, process.destroyCount);
      assertEquals(0, process.readerLinesInProgressAtDestroy);
      assertFalse(engine.isStarted());

      invokeTerminal(engine, binding);
      assertEquals(1, process.destroyCount);
      SwingUtilities.invokeAndWait(() -> {});
    }
  }

  @Test
  void currentStderrEofDoesNotDiscardAlreadyArrivedStdoutTail() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      RecordingProcess process = new RecordingProcess();
      setField(engine, "process", process);
      initializeStreams(engine, bytes("tail-frame\n"), bytes(""));
      Lizzie.leelaz = engine;
      engine.started = true;
      engine.isLoaded = true;
      engine.isNormalEnd = true;

      Method readError = Leelaz.class.getDeclaredMethod("readError");
      readError.setAccessible(true);
      readError.invoke(engine);

      assertEquals(0, process.destroyCount);
      assertTrue(engine.isStarted());

      Method read = Leelaz.class.getDeclaredMethod("read");
      read.setAccessible(true);
      read.invoke(engine);

      assertTrue(recentLines(engine, "recentStdoutLines").contains("tail-frame"));
      assertEquals(1, process.destroyCount);
      assertFalse(engine.isStarted());
    }
  }

  @Test
  void currentStderrReadFailureOnlyEndsStderrReader() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      RecordingProcess process = new RecordingProcess();
      InputStream failingStderr =
          new InputStream() {
            @Override
            public int read() throws IOException {
              throw new IOException("controlled current stderr failure");
            }
          };
      setField(engine, "process", process);
      initializeStreams(engine, bytes(""), failingStderr);
      engine.started = true;
      engine.isNormalEnd = true;

      Method readError = Leelaz.class.getDeclaredMethod("readError");
      readError.setAccessible(true);
      readError.invoke(engine);

      assertEquals(0, process.destroyCount);
      assertTrue(engine.isStarted());
    }
  }

  @Test
  void genmovePkFollowUpReadsFromCapturedStdoutReader() throws Exception {
    try (GlobalState ignored = GlobalState.install()) {
      Leelaz engine = new Leelaz("");
      BufferedReader reboundReader =
          new BufferedReader(new InputStreamReader(bytes("D4\n"), StandardCharsets.UTF_8));
      setField(engine, "inputStream", reboundReader);
      setField(engine, "currentEngineN", 0);
      Lizzie.leelaz = engine;
      EngineManager.engineGameInfo = new EngineGameInfo();
      EngineManager.engineGameInfo.blackEngineIndex = 0;
      EngineManager.engineGameInfo.whiteEngineIndex = 1;
      EngineManager.isEngineGame = true;
      Lizzie.board = new Board();
      BufferedReader capturedReader =
          new BufferedReader(
              new InputStreamReader(bytes("not-a-coordinate\n"), StandardCharsets.UTF_8));

      Method method =
          Leelaz.class.getDeclaredMethod(
              "parseLineForGenmovePk", String.class, BufferedReader.class);
      method.setAccessible(true);
      method.invoke(engine, "= Passing", capturedReader);

      assertEquals("D4", reboundReader.readLine());
      assertEquals(null, capturedReader.readLine());
    }
  }

  private static Thread invokeInThread(
      Leelaz engine, String methodName, AtomicReference<Throwable> failure) {
    Thread thread =
        new Thread(
            () -> {
              try {
                Method method = Leelaz.class.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(engine);
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "test-" + methodName);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  private static void initializeStreams(Leelaz engine, InputStream stdout, InputStream stderr)
      throws Exception {
    Method method =
        Leelaz.class.getDeclaredMethod(
            "initializeStreams", InputStream.class, OutputStream.class, InputStream.class);
    method.setAccessible(true);
    method.invoke(engine, stdout, new ByteArrayOutputStream(), stderr);
  }

  private static void invokeTerminal(Leelaz engine, Object binding) throws Exception {
    Method method =
        Leelaz.class.getDeclaredMethod(
            "terminateReaderIncarnation", binding.getClass(), Throwable.class);
    method.setAccessible(true);
    method.invoke(engine, binding, null);
  }

  private static BufferedReader currentReader(Leelaz engine, String name) throws Exception {
    return (BufferedReader) getField(engine, name);
  }

  private static int readerLinesInProgress(Object binding) throws Exception {
    Field field = binding.getClass().getDeclaredField("linesInProgress");
    field.setAccessible(true);
    return field.getInt(binding);
  }

  @SuppressWarnings("unchecked")
  private static ArrayDeque<String> recentLines(Leelaz engine, String name) throws Exception {
    return (ArrayDeque<String>) getField(engine, name);
  }

  private static Object getField(Leelaz engine, String name) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(engine);
  }

  private static void setField(Leelaz engine, String name, Object value) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(engine, value);
  }

  private static ByteArrayInputStream bytes(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  private static <T> T allocate(Class<T> type) throws InstantiationException {
    return type.cast(UnsafeHolder.UNSAFE.allocateInstance(type));
  }

  private static final class GlobalState implements AutoCloseable {
    private final Config previousConfig;
    private final Leelaz previousLeelaz;
    private final GtpConsolePane previousGtpConsole;
    private final Board previousBoard;
    private final boolean previousEngineGame;
    private final EngineGameInfo previousEngineGameInfo;

    private GlobalState(
        Config previousConfig,
        Leelaz previousLeelaz,
        GtpConsolePane previousGtpConsole,
        Board previousBoard,
        boolean previousEngineGame,
        EngineGameInfo previousEngineGameInfo) {
      this.previousConfig = previousConfig;
      this.previousLeelaz = previousLeelaz;
      this.previousGtpConsole = previousGtpConsole;
      this.previousBoard = previousBoard;
      this.previousEngineGame = previousEngineGame;
      this.previousEngineGameInfo = previousEngineGameInfo;
    }

    private static GlobalState install() throws Exception {
      GlobalState state =
          new GlobalState(
              Lizzie.config,
              Lizzie.leelaz,
              Lizzie.gtpConsole,
              Lizzie.board,
              EngineManager.isEngineGame,
              EngineManager.engineGameInfo);
      Lizzie.config =
          ConfigTestHelper.createForTests(Files.createTempDirectory("leelaz-reader-incarnation"));
      Lizzie.gtpConsole = allocate(SilentGtpConsole.class);
      EngineManager.isEngineGame = false;
      return state;
    }

    @Override
    public void close() {
      Lizzie.config = previousConfig;
      Lizzie.leelaz = previousLeelaz;
      Lizzie.gtpConsole = previousGtpConsole;
      Lizzie.board = previousBoard;
      EngineManager.isEngineGame = previousEngineGame;
      EngineManager.engineGameInfo = previousEngineGameInfo;
    }
  }

  private static final class SilentGtpConsole extends GtpConsolePane {
    private SilentGtpConsole() {
      super(null);
    }

    @Override
    public boolean isVisible() {
      return false;
    }

    @Override
    public void addLine(String line) {}

    @Override
    public void addErrorLine(String line) {}
  }

  private static final class BlockingGtpConsole extends GtpConsolePane {
    private CountDownLatch lineEntered;
    private CountDownLatch lineReleased;

    private BlockingGtpConsole() {
      super(null);
    }

    private void initialize() {
      lineEntered = new CountDownLatch(1);
      lineReleased = new CountDownLatch(1);
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    public void addLine(String line) {
      blockLine();
    }

    @Override
    public void addErrorLine(String line) {
      blockLine();
    }

    private void blockLine() {
      lineEntered.countDown();
      try {
        if (!lineReleased.await(2, TimeUnit.SECONDS)) {
          throw new AssertionError("timed out waiting to release parser line");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }

    private boolean awaitLine() throws InterruptedException {
      return lineEntered.await(1, TimeUnit.SECONDS);
    }

    private void releaseLine() {
      lineReleased.countDown();
    }
  }

  private static final class CleanupRecordingLeelaz extends Leelaz {
    private int readBoardCleanupCount;

    private CleanupRecordingLeelaz() throws IOException {
      super("");
    }

    @Override
    public void failReadBoardGmaEngineRestore(String detail) {
      readBoardCleanupCount++;
      super.failReadBoardGmaEngineRestore(detail);
    }
  }

  private static final class BlockingInputStream extends InputStream {
    private final byte[] payload;
    private final boolean fail;
    private final CountDownLatch reading = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private int offset;

    private BlockingInputStream(String payload) {
      this(payload, false);
    }

    private BlockingInputStream(String payload, boolean fail) {
      this.payload = payload.getBytes(StandardCharsets.UTF_8);
      this.fail = fail;
    }

    @Override
    public int read(byte[] buffer, int targetOffset, int length) throws IOException {
      reading.countDown();
      try {
        if (!released.await(2, TimeUnit.SECONDS)) {
          throw new IOException("timed out waiting to release test reader");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IOException(interrupted);
      }
      if (fail) {
        throw new IOException("controlled stale reader failure");
      }
      if (offset >= payload.length) {
        return -1;
      }
      int copied = Math.min(length, payload.length - offset);
      System.arraycopy(payload, offset, buffer, targetOffset, copied);
      offset += copied;
      return copied;
    }

    @Override
    public int read() throws IOException {
      byte[] single = new byte[1];
      return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
    }

    private boolean awaitRead() throws InterruptedException {
      return reading.await(1, TimeUnit.SECONDS);
    }

    private void release() {
      released.countDown();
    }
  }

  private static class RecordingProcess extends Process {
    private int destroyCount;
    private Leelaz readerOwner;
    private int readerLinesInProgressAtDestroy = -1;

    private void observeReaderLines(Leelaz owner) {
      readerOwner = owner;
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return bytes("");
    }

    @Override
    public InputStream getErrorStream() {
      return bytes("");
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
      destroyCount++;
      if (readerOwner != null) {
        try {
          Object binding = getField(readerOwner, "readerStreamBinding");
          Field linesInProgress = binding.getClass().getDeclaredField("linesInProgress");
          linesInProgress.setAccessible(true);
          readerLinesInProgressAtDestroy = linesInProgress.getInt(binding);
        } catch (Exception reflectionFailure) {
          throw new AssertionError(reflectionFailure);
        }
      }
    }

    @Override
    public boolean isAlive() {
      return true;
    }
  }

  private static final class ThrowingProcess extends RecordingProcess {
    @Override
    public void destroy() {
      super.destroy();
      throw new IllegalStateException("controlled process destroy failure");
    }
  }

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE = loadUnsafe();

    private static sun.misc.Unsafe loadUnsafe() {
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("Failed to access Unsafe", ex);
      }
    }
  }
}
