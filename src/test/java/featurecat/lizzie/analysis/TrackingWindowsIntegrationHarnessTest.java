package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class TrackingWindowsIntegrationHarnessTest {
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
}
