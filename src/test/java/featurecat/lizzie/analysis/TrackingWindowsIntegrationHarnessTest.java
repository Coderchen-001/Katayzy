package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ExtraMode;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.Utils;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class TrackingWindowsIntegrationHarnessTest {
  private static final int REAL_WARMUPS = 3;
  private static final int REAL_SAMPLES = 30;

  @Test
  void controlledTransportWritesRawMonotonicAcquisitionAndHandoffSamples() throws Exception {
    try (ControlledTransport transport = ControlledTransport.open();
        AsyncThrowableCapture failures = AsyncThrowableCapture.install()) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        TrackingAnalysisController controller = new TrackingAnalysisController();
        TrackingAnalysisController.Context context = transport.context();
        long addRequested = System.nanoTime();
        Future<TrackingAnalysisController.AddResult> add =
            executor.submit(() -> controller.addPoint("D4", context));

        assertEquals(TrackingAnalysisController.AddResult.ADDED, failures.await(add));
        transport.completeInitialFence(800000000);
        long initialFenceReady = System.nanoTime();
        assertTrue(transport.commands().contains("kata-analyze 10 allow B D4 1 allow W D4 1"));

        AtomicLong activated = new AtomicLong();
        Leelaz.TrackingHandoffTarget target =
            new Leelaz.TrackingHandoffTarget() {
              @Override
              public Leelaz.TrackingHandoffKind kind() {
                return Leelaz.TrackingHandoffKind.RETAINED_ENGINE_MODE;
              }

              @Override
              public boolean isCurrent() {
                return true;
              }

              @Override
              public void activate(Leelaz.TrackingHandoffActivation activation) {
                activated.set(System.nanoTime());
                assertTrue(activation.completeRetainedEngineMode());
              }

              @Override
              public void fail(Leelaz.TrackingHandoffFailure failure) {
                throw new AssertionError("controlled handoff failed: " + failure);
              }
            };
        long handoffClaimed = System.nanoTime();
        Leelaz.TrackingHandoffClaim claim = transport.engine.claimTrackingHandoff(target);
        assertEquals(Leelaz.TrackingHandoffAvailability.ACCEPTED_PENDING, claim.availability());
        transport.completeFinalFence(800000002);

        assertTrue(activated.get() >= handoffClaimed);
        assertTrue(controller.snapshot().selectedPoints().isEmpty());
        writeRawSamples(initialFenceReady - addRequested, activated.get() - handoffClaimed, 0L);
        failures.assertNoFailures();
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void harnessReportsWorkerEdtAndExecutorThrowablesAndRestoresHandlers() throws Exception {
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    AsyncThrowableCapture failures = AsyncThrowableCapture.install();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Thread worker =
          new Thread(
              () -> {
                throw new IllegalStateException("controlled worker failure");
              });
      worker.start();
      worker.join();

      CountDownLatch edtRan = new CountDownLatch(1);
      EventQueue.invokeLater(
          () -> {
            edtRan.countDown();
            throw new IllegalArgumentException("controlled EDT failure");
          });
      assertTrue(edtRan.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {});

      Future<Void> future =
          executor.submit(
              () -> {
                throw new UnsupportedOperationException("controlled executor failure");
              });
      failures.await(future);

      assertThrows(AssertionError.class, failures::assertNoFailures);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      failures.close();
    }
    assertSame(previous, Thread.getDefaultUncaughtExceptionHandler());
  }

  @Test
  void realKataGoGateRunsThirtySamplesWhenCommandIsConfigured() throws Exception {
    String command =
        System.getProperty(
            "tracking.real.command", System.getenv().getOrDefault("TRACKING_REAL_COMMAND", ""));
    if (command.isBlank()) {
      return;
    }
    try (AsyncThrowableCapture failures = AsyncThrowableCapture.install();
        RealKataGoTransport transport = RealKataGoTransport.open(command, failures)) {
      List<TimingSample> samples = new ArrayList<>();
      for (int index = 0; index < REAL_WARMUPS + REAL_SAMPLES; index++) {
        TimingSample sample = transport.measure(index - REAL_WARMUPS + 1);
        if (index >= REAL_WARMUPS) {
          samples.add(sample);
        }
      }

      writeRealSamples(samples);
      assertTimingGate(samples);
      failures.assertNoFailures();
    }
  }

  private static void writeRawSamples(
      long acquisitionNanos, long handoffNanos, long targetOperationNanos) throws Exception {
    Path outputDirectory = Path.of("target", "tracking-windows-harness");
    Files.createDirectories(outputDirectory);
    Files.writeString(
        outputDirectory.resolve("controlled-samples.csv"),
        "sample,acquisition_ns,handoff_ns,target_operation_ns\n"
            + "1,"
            + acquisitionNanos
            + ","
            + handoffNanos
            + ","
            + targetOperationNanos
            + "\n",
        StandardCharsets.UTF_8);
    JSONObject sample =
        new JSONObject()
            .put("sample", 1)
            .put("acquisition_ns", acquisitionNanos)
            .put("handoff_ns", handoffNanos)
            .put("target_operation_ns", targetOperationNanos);
    Files.writeString(
        outputDirectory.resolve("controlled-samples.json"),
        new JSONArray().put(sample).toString(2) + "\n",
        StandardCharsets.UTF_8);
  }

  private static void writeRealSamples(List<TimingSample> samples) throws Exception {
    String configuredOutput = System.getProperty("tracking.real.output", "");
    Path outputDirectory =
        configuredOutput.isBlank()
            ? Path.of("target", "tracking-windows-harness")
            : Path.of(configuredOutput);
    Files.createDirectories(outputDirectory);
    StringBuilder csv =
        new StringBuilder(
            "sample,acquisition_ns,handoff_ns,target_operation_ns,timeout,terminal_recovery,second_click\n");
    JSONArray rows = new JSONArray();
    for (TimingSample sample : samples) {
      csv.append(sample.number)
          .append(',')
          .append(sample.acquisitionNanos)
          .append(',')
          .append(sample.handoffNanos)
          .append(',')
          .append(sample.targetOperationNanos)
          .append(",0,0,0\n");
      rows.put(sample.toJson());
    }
    JSONObject summary =
        new JSONObject()
            .put("warmups", REAL_WARMUPS)
            .put("sample_count", samples.size())
            .put("acquisition", timingSummary(samples, true))
            .put("handoff", timingSummary(samples, false))
            .put("timeouts", 0)
            .put("terminal_recoveries", 0)
            .put("second_clicks", 0);
    Files.writeString(
        outputDirectory.resolve("real-samples.csv"), csv.toString(), StandardCharsets.UTF_8);
    Files.writeString(
        outputDirectory.resolve("real-samples.json"),
        new JSONObject().put("samples", rows).put("summary", summary).toString(2) + "\n",
        StandardCharsets.UTF_8);
  }

  private static JSONObject timingSummary(List<TimingSample> samples, boolean acquisition) {
    List<Long> values =
        samples.stream()
            .map(sample -> acquisition ? sample.acquisitionNanos : sample.handoffNanos)
            .sorted(Comparator.naturalOrder())
            .toList();
    return new JSONObject()
        .put("p50_ns", nearestRank(values, 0.50))
        .put("p95_ns", nearestRank(values, 0.95))
        .put("max_ns", values.get(values.size() - 1));
  }

  private static long nearestRank(List<Long> sorted, double percentile) {
    int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
    return sorted.get(index);
  }

  private static void assertTimingGate(List<TimingSample> samples) {
    assertEquals(REAL_SAMPLES, samples.size());
    List<Long> acquisition =
        samples.stream().map(sample -> sample.acquisitionNanos).sorted().toList();
    List<Long> handoff = samples.stream().map(sample -> sample.handoffNanos).sorted().toList();
    assertTrue(nearestRank(acquisition, 0.95) <= TimeUnit.SECONDS.toNanos(2));
    assertTrue(nearestRank(handoff, 0.95) <= TimeUnit.SECONDS.toNanos(2));
    assertTrue(acquisition.get(acquisition.size() - 1) <= TimeUnit.SECONDS.toNanos(4));
    assertTrue(handoff.get(handoff.size() - 1) <= TimeUnit.SECONDS.toNanos(4));
  }

  private static final class TimingSample {
    private final int number;
    private final long acquisitionNanos;
    private final long handoffNanos;
    private final long targetOperationNanos;

    private TimingSample(
        int number, long acquisitionNanos, long handoffNanos, long targetOperationNanos) {
      this.number = number;
      this.acquisitionNanos = acquisitionNanos;
      this.handoffNanos = handoffNanos;
      this.targetOperationNanos = targetOperationNanos;
    }

    private JSONObject toJson() {
      return new JSONObject()
          .put("sample", number)
          .put("acquisition_ns", acquisitionNanos)
          .put("handoff_ns", handoffNanos)
          .put("target_operation_ns", targetOperationNanos)
          .put("timeout", false)
          .put("terminal_recovery", false)
          .put("second_click", false);
    }
  }

  private static final class AsyncThrowableCapture implements AutoCloseable {
    private final Thread.UncaughtExceptionHandler previousHandler;
    private final CapturingEventQueue eventQueue;
    private final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    private boolean closed;

    private AsyncThrowableCapture(Thread.UncaughtExceptionHandler previousHandler) {
      this.previousHandler = previousHandler;
      this.eventQueue = new CapturingEventQueue(failures);
    }

    static AsyncThrowableCapture install() {
      Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
      AsyncThrowableCapture capture = new AsyncThrowableCapture(previous);
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(capture.eventQueue);
      Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> capture.failures.add(failure));
      return capture;
    }

    <T> T await(Future<T> future) throws InterruptedException {
      try {
        return future.get();
      } catch (ExecutionException failure) {
        failures.add(failure.getCause());
        return null;
      }
    }

    <T> T await(Future<T> future, long timeout, TimeUnit unit) throws InterruptedException {
      try {
        return future.get(timeout, unit);
      } catch (ExecutionException failure) {
        failures.add(failure.getCause());
      } catch (java.util.concurrent.TimeoutException failure) {
        failures.add(failure);
        future.cancel(true);
      }
      return null;
    }

    void assertNoFailures() {
      synchronized (failures) {
        if (!failures.isEmpty()) {
          AssertionError error =
              new AssertionError("captured asynchronous Throwable: " + failures.get(0));
          failures.forEach(error::addSuppressed);
          throw error;
        }
      }
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      Thread.setDefaultUncaughtExceptionHandler(previousHandler);
      eventQueue.restore();
    }
  }

  private static final class CapturingEventQueue extends EventQueue {
    private final List<Throwable> failures;

    private CapturingEventQueue(List<Throwable> failures) {
      this.failures = failures;
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
      try {
        super.dispatchEvent(event);
      } catch (Throwable failure) {
        failures.add(failure);
      }
    }

    private void restore() {
      super.pop();
    }
  }

  private static final class RealKataGoTransport implements AutoCloseable {
    private final Leelaz previousEngine;
    private final Config previousConfig;
    private final boolean previousEmpty;
    private final boolean previousEngineGame;
    private final Leelaz engine;
    private final Process process;
    private final BufferedOutputStream output;
    private final RecordingOutputStream recordingOutput;
    private final Thread readerThread;
    private final Thread stderrThread;

    private RealKataGoTransport(
        Leelaz previousEngine,
        Config previousConfig,
        boolean previousEmpty,
        boolean previousEngineGame,
        Leelaz engine,
        Process process,
        BufferedOutputStream output,
        RecordingOutputStream recordingOutput,
        Thread readerThread,
        Thread stderrThread) {
      this.previousEngine = previousEngine;
      this.previousConfig = previousConfig;
      this.previousEmpty = previousEmpty;
      this.previousEngineGame = previousEngineGame;
      this.engine = engine;
      this.process = process;
      this.output = output;
      this.recordingOutput = recordingOutput;
      this.readerThread = readerThread;
      this.stderrThread = stderrThread;
    }

    static RealKataGoTransport open(String command, AsyncThrowableCapture failures)
        throws Exception {
      Leelaz previousEngine = Lizzie.leelaz;
      Config previousConfig = Lizzie.config;
      boolean previousEmpty = EngineManager.isEmpty;
      boolean previousEngineGame = EngineManager.isEngineGame;
      Config config = allocate(Config.class);
      config.extraMode = ExtraMode.Normal;
      config.autoCheckEngineAlive = false;
      Lizzie.config = config;
      EngineManager.isEmpty = false;
      EngineManager.isEngineGame = false;

      Process process = new ProcessBuilder(Utils.splitCommand(command)).start();
      BufferedReader stdout =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
      BufferedReader stderr =
          new BufferedReader(
              new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
      Thread stderrThread = new Thread(() -> drain(stderr), "tracking-real-katago-stderr");
      stderrThread.start();
      RecordingOutputStream recordingOutput = new RecordingOutputStream(process.getOutputStream());
      BufferedOutputStream output = new BufferedOutputStream(recordingOutput);

      ExecutorService warmupExecutor = Executors.newSingleThreadExecutor();
      try {
        output.write("name\n".getBytes(StandardCharsets.UTF_8));
        output.flush();
        Future<String> response = warmupExecutor.submit(() -> readGtpResponse(stdout));
        String name = failures.await(response, 180, TimeUnit.SECONDS);
        assertTrue(name != null && name.startsWith("="), "real KataGo did not answer name");
      } finally {
        warmupExecutor.shutdownNow();
        assertTrue(warmupExecutor.awaitTermination(5, TimeUnit.SECONDS));
      }

      Leelaz engine = new Leelaz(command);
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      setField(engine, "process", process);
      setField(engine, "inputStream", stdout);
      setField(engine, "outputStream", output);
      setField(engine, "endGetCommandList", true);
      Lizzie.leelaz = engine;
      Thread readerThread = new Thread(() -> invokeReader(engine), "tracking-real-katago-stdout");
      readerThread.start();
      return new RealKataGoTransport(
          previousEngine,
          previousConfig,
          previousEmpty,
          previousEngineGame,
          engine,
          process,
          output,
          recordingOutput,
          readerThread,
          stderrThread);
    }

    TimingSample measure(int sampleNumber) throws Exception {
      CountDownLatch analyzeWritten = recordingOutput.prepareAnalyze();
      TrackingAnalysisController controller = new TrackingAnalysisController();
      TrackingAnalysisController.Context context =
          new TrackingAnalysisController.Context(
              this,
              new Object(),
              19,
              19,
              "real-katago-stones",
              true,
              "chinese",
              7.5,
              engine,
              engine.trackingStreamIncarnation(),
              new TrackingAnalysisController.Parameters(10, 1_000_000),
              null);
      long addRequested = System.nanoTime();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      assertTrue(analyzeWritten.await(8, TimeUnit.SECONDS), "initial fence did not become ready");
      long initialFenceReady = System.nanoTime();

      CountDownLatch activated = new CountDownLatch(1);
      AtomicLong activatedAt = new AtomicLong();
      AtomicLong targetOperationNanos = new AtomicLong();
      AtomicReference<Throwable> targetFailure = new AtomicReference<>();
      Leelaz.TrackingHandoffTarget target =
          new Leelaz.TrackingHandoffTarget() {
            @Override
            public Leelaz.TrackingHandoffKind kind() {
              return Leelaz.TrackingHandoffKind.RETAINED_ENGINE_MODE;
            }

            @Override
            public boolean isCurrent() {
              return true;
            }

            @Override
            public void activate(Leelaz.TrackingHandoffActivation activation) {
              long start = System.nanoTime();
              activatedAt.set(start);
              if (!activation.completeRetainedEngineMode()) {
                targetFailure.set(new AssertionError("real handoff activation was rejected"));
              }
              targetOperationNanos.set(System.nanoTime() - start);
              activated.countDown();
            }

            @Override
            public void fail(Leelaz.TrackingHandoffFailure failure) {
              targetFailure.set(new AssertionError("real handoff failed: " + failure));
              activated.countDown();
            }
          };
      long handoffClaimed = System.nanoTime();
      Leelaz.TrackingHandoffClaim claim = engine.claimTrackingHandoff(target);
      assertEquals(Leelaz.TrackingHandoffAvailability.ACCEPTED_PENDING, claim.availability());
      assertTrue(activated.await(8, TimeUnit.SECONDS), "final fence did not activate target");
      if (targetFailure.get() != null) {
        throw new AssertionError(targetFailure.get());
      }
      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      return new TimingSample(
          sampleNumber,
          initialFenceReady - addRequested,
          activatedAt.get() - handoffClaimed,
          targetOperationNanos.get());
    }

    @Override
    public void close() throws Exception {
      try {
        engine.isNormalEnd = true;
        if (process.isAlive()) {
          output.write("quit\n".getBytes(StandardCharsets.UTF_8));
          output.flush();
          if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
          }
        }
        readerThread.join(TimeUnit.SECONDS.toMillis(10));
        stderrThread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(readerThread.isAlive());
        assertFalse(stderrThread.isAlive());
      } finally {
        Lizzie.leelaz = previousEngine;
        Lizzie.config = previousConfig;
        EngineManager.isEmpty = previousEmpty;
        EngineManager.isEngineGame = previousEngineGame;
      }
    }

    private static String readGtpResponse(BufferedReader reader) throws IOException {
      StringBuilder response = new StringBuilder();
      String line;
      boolean started = false;
      while ((line = reader.readLine()) != null) {
        if (!started && (line.startsWith("=") || line.startsWith("?"))) {
          started = true;
        }
        if (started) {
          response.append(line).append('\n');
          if (line.isEmpty()) {
            return response.toString();
          }
        }
      }
      return response.toString();
    }

    private static void invokeReader(Leelaz engine) {
      try {
        Method method = Leelaz.class.getDeclaredMethod("read");
        method.setAccessible(true);
        method.invoke(engine);
      } catch (InvocationTargetException failure) {
        throw new RuntimeException(failure.getCause());
      } catch (ReflectiveOperationException failure) {
        throw new RuntimeException(failure);
      }
    }

    private static void drain(BufferedReader reader) {
      try {
        while (reader.readLine() != null) {}
      } catch (IOException failure) {
        throw new RuntimeException(failure);
      }
    }
  }

  private static final class RecordingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final StringBuilder line = new StringBuilder();
    private CountDownLatch analyzeWritten;

    private RecordingOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    synchronized CountDownLatch prepareAnalyze() {
      analyzeWritten = new CountDownLatch(1);
      return analyzeWritten;
    }

    @Override
    public synchronized void write(int value) throws IOException {
      delegate.write(value);
      record(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
      delegate.write(bytes, offset, length);
      for (int index = offset; index < offset + length; index++) {
        record(bytes[index]);
      }
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    private void record(int value) {
      if (value == '\n') {
        if (line.indexOf("kata-analyze") >= 0 && analyzeWritten != null) {
          analyzeWritten.countDown();
        }
        line.setLength(0);
      } else if (value != '\r') {
        line.append((char) (value & 0xff));
      }
    }
  }

  private static final class ControlledTransport implements AutoCloseable {
    private final Leelaz previousEngine;
    private final Leelaz engine;
    private final ByteArrayOutputStream output;

    private ControlledTransport(
        Leelaz previousEngine, Leelaz engine, ByteArrayOutputStream output) {
      this.previousEngine = previousEngine;
      this.engine = engine;
      this.output = output;
    }

    static ControlledTransport open() throws Exception {
      Leelaz previous = Lizzie.leelaz;
      Leelaz engine = new Leelaz("");
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      setField(engine, "endGetCommandList", true);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      setField(engine, "outputStream", new BufferedOutputStream(output));
      Lizzie.leelaz = engine;
      return new ControlledTransport(previous, engine, output);
    }

    TrackingAnalysisController.Context context() {
      return new TrackingAnalysisController.Context(
          this,
          output,
          19,
          19,
          "controlled-stones",
          true,
          "chinese",
          7.5,
          engine,
          engine.trackingStreamIncarnation(),
          new TrackingAnalysisController.Parameters(10, 100),
          null);
    }

    void completeInitialFence(int commandId) throws Exception {
      assertFalse(dispatch("=" + commandId));
      processCommandResponse("=" + commandId);
      assertTrue(dispatch(""));
    }

    void completeFinalFence(int commandId) throws Exception {
      assertTrue(dispatch(""));
      assertTrue(dispatch("=" + commandId));
      assertTrue(dispatch(""));
    }

    String commands() {
      return output.toString(StandardCharsets.UTF_8);
    }

    private boolean dispatch(String line) throws Exception {
      Method method = Leelaz.class.getDeclaredMethod("dispatchExclusiveGtpLine", String.class);
      method.setAccessible(true);
      return (boolean) method.invoke(engine, line);
    }

    private void processCommandResponse(String line) throws Exception {
      Method method = Leelaz.class.getDeclaredMethod("processCommandResponseLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    @Override
    public void close() {
      Lizzie.leelaz = previousEngine;
    }
  }

  private static void setField(Leelaz engine, String name, Object value) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(engine, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }
}
