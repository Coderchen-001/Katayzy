package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrackingAnalysisControllerTest {

  @Test
  void addPointAcquiresLeaseAndSendsConstrainedAnalyzeAfterInitialFence() throws Exception {
    try (TestState state = TestState.open()) {
      ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
      TrackingAnalysisController controller = new TrackingAnalysisController(scheduler);
      TrackingAnalysisController.Context context =
          new TrackingAnalysisController.Context(
              new Object(),
              new Object(),
              19,
              19,
              "stones",
              true,
              "chinese",
              7.5,
              state.engine,
              state.engine.trackingStreamIncarnation(),
              new TrackingAnalysisController.Parameters(10, 100),
              null);

      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("d4", context));
      assertEquals("800000000 stop\n", state.commands());

      completeInitialFence(state.engine);

      assertEquals(
          "800000000 stop\n" + "800000001 kata-analyze 10 allow B D4 1 allow W D4 1\n",
          state.commands());
      assertEquals(List.of("D4"), new ArrayList<>(controller.snapshot().selectedPoints()));
      assertTrue(controller.snapshot().active());
      assertEquals(1, scheduler.pendingCount());
    }
  }

  @Test
  void pendingPointsRunNewestFirstAndRemovingPendingDoesNotInterruptCurrent() throws Exception {
    try (TestState state = TestState.open()) {
      ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
      TrackingAnalysisController controller = new TrackingAnalysisController(scheduler);
      TrackingAnalysisController.Context context = state.context();

      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("F6", context));
      assertTrue(controller.removePoint("E5"));
      assertEquals(List.of("D4", "F6"), new ArrayList<>(controller.snapshot().selectedPoints()));

      assertTrue(dispatch(state.engine, "info move D4 visits 100 winrate 0.55 pv D4 Q16"));
      completeFinalFence(state.engine, 800000002);
      assertTrue(state.commands().endsWith("800000003 stop\n"));
      completeInitialFence(state.engine, 800000003);

      assertTrue(
          state.commands().endsWith("800000004 kata-analyze 10 allow B F6 1 allow W F6 1\n"));
      assertEquals(100, controller.snapshot().results().get("D4").visits());
      assertTrue(controller.snapshot().results().get("D4").completed());
      assertFalse(controller.snapshot().results().containsKey("E5"));
    }
  }

  @Test
  void contextChangeClearsDisplayAndStaleCallbacksCannotRestoreIt() throws Exception {
    RecordingPonderLeelaz engine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(engine)) {
      engine.Pondering();
      ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
      TrackingAnalysisController controller = new TrackingAnalysisController(scheduler);
      TrackingAnalysisController.Context original = state.context();

      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", original));
      completeInitialFence(state.engine, 800000000);
      assertTrue(dispatch(state.engine, "info move D4 visits 40 winrate 0.51 pv D4"));
      ManualTimeoutScheduler.ManualTimeout staleTimeout = scheduler.latest();
      assertEquals(40, controller.snapshot().results().get("D4").visits());

      controller.contextChanged(state.contextWithStones("changed"));

      assertTrue(
          controller.snapshot().selectedPoints().isEmpty(),
          controller.snapshot().selectedPoints() + " commands=" + state.commands());
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().active());
      assertTrue(state.commands().endsWith("800000002 stop\n"));

      assertTrue(dispatch(state.engine, "info move D4 visits 100 winrate 0.99 pv D4"));
      runTimeoutOnWorker(staleTimeout);
      completeFinalFence(state.engine, 800000002);

      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().active());
      assertEquals(0, engine.ponderCount.get());
    }
  }

  @Test
  void naturalCompletionHandsInitialPonderBackExactlyOnce() throws Exception {
    RecordingPonderLeelaz engine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(engine)) {
      engine.Pondering();
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());

      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", state.context()));
      completeInitialFence(engine, 800000000);
      assertTrue(dispatch(engine, "info move D4 visits 100 winrate 0.55 pv D4"));
      completeFinalFence(engine, 800000002);

      assertEquals(1, engine.ponderCount.get());
      assertEquals(100, controller.snapshot().results().get("D4").visits());
      assertFalse(controller.snapshot().active());
    }
  }

  @Test
  void safeOrdinaryReleaseFreezesThenOrdinaryUpgradeClearsWithoutReacquire() throws Exception {
    RecordingPonderLeelaz engine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(engine)) {
      engine.Pondering();
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));
      assertTrue(dispatch(state.engine, "info move D4 visits 40 winrate 0.51 pv D4"));

      assertTrue(state.engine.sendRawConsoleCommand("version"));

      assertTrue(controller.snapshot().frozen());
      assertFalse(controller.snapshot().active());
      assertEquals(List.of("D4"), new ArrayList<>(controller.snapshot().selectedPoints()));
      assertEquals(40, controller.snapshot().results().get("D4").visits());

      state.engine.sendCommand("boardsize 19");

      assertFalse(controller.snapshot().frozen());
      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      completeFinalFence(state.engine, 800000002);
      assertFalse(state.commands().contains("allow B E5"));

      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      assertEquals(0, engine.ponderCount.get());
    }
  }

  @Test
  void duplicateIllegalAndMismatchedAddsAreRejectedWithoutChangingCurrent() throws Exception {
    try (TestState state = TestState.open()) {
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));

      assertEquals(
          TrackingAnalysisController.AddResult.DUPLICATE, controller.addPoint("d4", context));
      assertEquals(
          TrackingAnalysisController.AddResult.ILLEGAL, controller.addPoint("(1,2)", context));
      assertEquals(
          TrackingAnalysisController.AddResult.ILLEGAL, controller.addPoint("I4", context));
      assertEquals(
          TrackingAnalysisController.AddResult.ILLEGAL, controller.addPoint("T20", context));
      assertEquals(
          TrackingAnalysisController.AddResult.CONTEXT_MISMATCH,
          controller.addPoint("E5", state.contextWithStones("other")));

      assertEquals(List.of("D4"), new ArrayList<>(controller.snapshot().selectedPoints()));
      assertEquals("800000000 stop\n", state.commands());
    }
  }

  @Test
  void onlyStrictTargetProgressRenewsTimeoutAndStaleTimerCannotRelease() throws Exception {
    try (TestState state = TestState.open()) {
      ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
      TrackingAnalysisController controller = new TrackingAnalysisController(scheduler);
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));
      ManualTimeoutScheduler.ManualTimeout initialTimeout = scheduler.latest();

      assertTrue(dispatch(state.engine, "info move Q16 visits 50 winrate 0.6 pv Q16"));
      assertEquals(1, scheduler.totalCount());
      assertTrue(dispatch(state.engine, "info move D4 visits 10 winrate 0.51 pv D4"));
      ManualTimeoutScheduler.ManualTimeout firstProgressTimeout = scheduler.latest();
      assertTrue(dispatch(state.engine, "info move D4 visits 10 winrate 0.52 pv D4"));
      assertTrue(dispatch(state.engine, "info move D4 visits 9 winrate 0.53 pv D4"));
      assertEquals(2, scheduler.totalCount());
      assertTrue(initialTimeout.cancelled);

      assertTrue(dispatch(state.engine, "info move D4 visits 20 winrate 0.54 pv D4"));
      ManualTimeoutScheduler.ManualTimeout currentTimeout = scheduler.latest();
      assertTrue(firstProgressTimeout.cancelled);
      runTimeoutOnWorker(firstProgressTimeout);
      assertFalse(state.commands().contains("800000002 stop"));

      runTimeoutOnWorker(currentTimeout);
      assertTrue(state.commands().endsWith("800000002 stop\n"));
      completeFinalFence(state.engine, 800000002);
      assertTrue(state.commands().endsWith("800000003 stop\n"));
      assertFalse(controller.snapshot().results().containsKey("D4"));
    }
  }

  @Test
  void newTrackingStartReplacesAClosedFrozenSnapshot() throws Exception {
    try (TestState state = TestState.open()) {
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertTrue(dispatch(state.engine, "info move D4 visits 40 winrate 0.51 pv D4"));
      assertTrue(state.engine.sendRawConsoleCommand("version"));
      completeFinalFence(state.engine, 800000002);
      assertTrue(controller.snapshot().frozen());
      state.engine.sendCommand("boardsize 19");
      assertTrue(controller.snapshot().frozen());
      assertEquals(List.of("D4"), new ArrayList<>(controller.snapshot().selectedPoints()));

      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));

      assertEquals(List.of("E5"), new ArrayList<>(controller.snapshot().selectedPoints()));
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().frozen());
      assertTrue(state.commands().endsWith("800000003 stop\n"));
    }
  }

  @Test
  void initialAndFinalFenceErrorsDropSchedulingStateWithoutPonderHandback() throws Exception {
    RecordingPonderLeelaz initialErrorEngine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(initialErrorEngine)) {
      initialErrorEngine.Pondering();
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));

      assertTrue(dispatch(initialErrorEngine, "?800000000 cannot stop"));

      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      assertEquals(0, initialErrorEngine.ponderCount.get());
    }

    RecordingPonderLeelaz finalErrorEngine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(finalErrorEngine)) {
      finalErrorEngine.Pondering();
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(finalErrorEngine, 800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));
      assertTrue(dispatch(finalErrorEngine, "info move D4 visits 100 winrate 0.55 pv D4"));

      assertTrue(dispatch(finalErrorEngine, ""));
      assertTrue(dispatch(finalErrorEngine, "?800000002 cannot stop"));

      assertTrue(
          controller.snapshot().selectedPoints().isEmpty(),
          controller.snapshot().selectedPoints() + " commands=" + state.commands());
      assertTrue(controller.snapshot().results().isEmpty());
      assertEquals(0, finalErrorEngine.ponderCount.get());
    }
  }

  @Test
  void displaySnapshotCarriesItsImmutableContextAndResultValues() throws Exception {
    try (TestState state = TestState.open()) {
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertTrue(
          dispatch(
              state.engine,
              "info move D4 visits 40 winrate 0.51 scoreLead 2.5 prior 0.2 pv D4 Q16"));
      TrackingAnalysisController.DisplaySnapshot snapshot = controller.snapshot();

      assertSame(context, snapshot.context());
      assertTrue(snapshot.generation() > 0L);
      assertSame(context.displayNodeIdentity(), snapshot.context().displayNodeIdentity());
      assertEquals(List.of("D4", "Q16"), snapshot.results().get("D4").variation());
      assertFalse(snapshot.results().get("D4").completed());
      assertThrows(
          UnsupportedOperationException.class, () -> snapshot.selectedPoints().remove("D4"));
      assertThrows(UnsupportedOperationException.class, () -> snapshot.results().clear());
      assertThrows(
          UnsupportedOperationException.class,
          () -> snapshot.results().get("D4").variation().add("E5"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "history",
        "node",
        "width",
        "height",
        "stones",
        "to-play",
        "rules",
        "komi",
        "engine",
        "parameters",
        "readboard"
      })
  void everyContextComponentInvalidatesCurrentTracking(String component) throws Exception {
    try (TestState state = TestState.open()) {
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", state.context()));
      completeInitialFence(state.engine, 800000000);

      controller.contextChanged(state.contextVariant(component));

      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().active());
      assertTrue(state.commands().endsWith("800000002 stop\n"));
      completeFinalFence(state.engine, 800000002);
      assertTrue(controller.snapshot().selectedPoints().isEmpty());
    }
  }

  @Test
  void controllerRemainsDormantAndLegacyTrackingOwnsProductionEntry() throws Exception {
    Path productionRoot = Path.of("src/main/java");
    List<Path> productionReferences;
    try (java.util.stream.Stream<Path> files = Files.walk(productionRoot)) {
      productionReferences =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .filter(
                  path -> !path.getFileName().toString().equals("TrackingAnalysisController.java"))
              .filter(
                  path -> {
                    try {
                      return Files.readString(path).contains("TrackingAnalysisController");
                    } catch (java.io.IOException failure) {
                      throw new java.io.UncheckedIOException(failure);
                    }
                  })
              .toList();
    }
    String rightClick =
        Files.readString(
            Path.of("src/main/java/featurecat/lizzie/gui/RightClickMenu.java"),
            StandardCharsets.UTF_8);
    String controllerSource =
        Files.readString(
            Path.of("src/main/java/featurecat/lizzie/analysis/TrackingAnalysisController.java"),
            StandardCharsets.UTF_8);

    assertTrue(productionReferences.isEmpty(), productionReferences.toString());
    assertTrue(rightClick.contains("ensureTrackingEngineWithWarning()"));
    assertTrue(rightClick.contains("triggerTrackingAnalysis()"));
    assertFalse(controllerSource.contains("AnalysisRequestBuilder"));
    assertFalse(controllerSource.contains("Lizzie.board"));
    assertFalse(controllerSource.contains("BoardHistory"));
    assertFalse(controllerSource.contains("ReadBoard."));
  }

  @Test
  void removingCurrentHidesItImmediatelyAndClearDropsAllPendingIntent() throws Exception {
    try (TestState state = TestState.open()) {
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      TrackingAnalysisController.Context context = state.context();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", context));
      completeInitialFence(state.engine, 800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, controller.addPoint("E5", context));
      assertTrue(dispatch(state.engine, "info move D4 visits 40 winrate 0.51 pv D4"));

      assertTrue(controller.removePoint("D4"));

      assertEquals(List.of("E5"), new ArrayList<>(controller.snapshot().selectedPoints()));
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().active());
      completeFinalFence(state.engine, 800000002);
      completeInitialFence(state.engine, 800000003);

      controller.clear();

      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(controller.snapshot().results().isEmpty());
      assertFalse(controller.snapshot().active());
      assertTrue(state.commands().endsWith("800000005 stop\n"));
      completeFinalFence(state.engine, 800000005);
      assertFalse(state.commands().contains("800000006 stop"));
    }
  }

  @Test
  void typedHandoffClearsDisplayBeforeActivationAndPreventsPonderHandback() throws Exception {
    RecordingPonderLeelaz engine = new RecordingPonderLeelaz();
    try (TestState state = TestState.open(engine)) {
      engine.Pondering();
      TrackingAnalysisController controller =
          new TrackingAnalysisController(new ManualTimeoutScheduler());
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, controller.addPoint("D4", state.context()));
      completeInitialFence(engine, 800000000);
      assertTrue(dispatch(engine, "info move D4 visits 40 winrate 0.51 pv D4"));
      AtomicInteger activations = new AtomicInteger();
      AtomicReference<Boolean> displayClearedAtActivation = new AtomicReference<>();
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
              displayClearedAtActivation.set(controller.snapshot().selectedPoints().isEmpty());
              if (activation.completeRetainedEngineMode()) {
                activations.incrementAndGet();
              }
            }

            @Override
            public void fail(Leelaz.TrackingHandoffFailure failure) {}
          };

      Leelaz.TrackingHandoffClaim claim = engine.claimTrackingHandoff(target);

      assertEquals(Leelaz.TrackingHandoffAvailability.ACCEPTED_PENDING, claim.availability());
      assertTrue(controller.snapshot().selectedPoints().isEmpty());
      assertTrue(dispatch(engine, ""));
      assertTrue(dispatch(engine, "=800000002"));
      assertTrue(dispatch(engine, ""));
      assertEquals(1, activations.get());
      assertEquals(Boolean.TRUE, displayClearedAtActivation.get());
      assertEquals(0, engine.ponderCount.get());
    }
  }

  private static void completeInitialFence(Leelaz engine) throws Exception {
    completeInitialFence(engine, 800000000);
  }

  private static void runTimeoutOnWorker(ManualTimeoutScheduler.ManualTimeout timeout)
      throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try {
                timeout.runEvenIfCancelled();
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "tracking-controller-timeout-test");
    worker.setDaemon(true);
    worker.start();
    worker.join(1000L);
    assertFalse(worker.isAlive());
    assertEquals(null, failure.get());
  }

  private static void completeInitialFence(Leelaz engine, int commandId) throws Exception {
    assertFalse(dispatch(engine, "=" + commandId));
    processCommandResponse(engine, "=" + commandId);
    assertTrue(dispatch(engine, ""));
  }

  private static void completeFinalFence(Leelaz engine, int commandId) throws Exception {
    assertTrue(dispatch(engine, ""));
    assertTrue(dispatch(engine, "=" + commandId));
    assertTrue(dispatch(engine, ""));
  }

  private static boolean dispatch(Leelaz engine, String line) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("dispatchExclusiveGtpLine", String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(engine, line);
  }

  private static void processCommandResponse(Leelaz engine, String line) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("processCommandResponseLine", String.class);
    method.setAccessible(true);
    method.invoke(engine, line);
  }

  private static final class ManualTimeoutScheduler
      implements TrackingAnalysisController.TimeoutScheduler {
    private final List<ManualTimeout> pending = new ArrayList<>();

    @Override
    public TrackingAnalysisController.Cancellable schedule(long delayMillis, Runnable task) {
      ManualTimeout timeout = new ManualTimeout(task);
      pending.add(timeout);
      return timeout;
    }

    int pendingCount() {
      return (int) pending.stream().filter(timeout -> !timeout.cancelled).count();
    }

    int totalCount() {
      return pending.size();
    }

    ManualTimeout latest() {
      return pending.get(pending.size() - 1);
    }

    static final class ManualTimeout implements TrackingAnalysisController.Cancellable {
      private final Runnable task;
      private boolean cancelled;

      private ManualTimeout(Runnable task) {
        this.task = task;
      }

      @Override
      public void cancel() {
        cancelled = true;
      }

      void runEvenIfCancelled() {
        task.run();
      }
    }
  }

  private static final class TestState implements AutoCloseable {
    private final Leelaz previousEngine;
    private final Board previousBoard;
    private final Config previousConfig;
    private final LizzieFrame previousFrame;
    private final Leelaz engine;
    private final ByteArrayOutputStream output;

    private TestState(
        Leelaz previousEngine,
        Board previousBoard,
        Config previousConfig,
        LizzieFrame previousFrame,
        Leelaz engine,
        ByteArrayOutputStream output) {
      this.previousEngine = previousEngine;
      this.previousBoard = previousBoard;
      this.previousConfig = previousConfig;
      this.previousFrame = previousFrame;
      this.engine = engine;
      this.output = output;
    }

    static TestState open() throws Exception {
      return open(new Leelaz(""));
    }

    static TestState open(Leelaz engine) throws Exception {
      Leelaz previousEngine = Lizzie.leelaz;
      Board previousBoard = Lizzie.board;
      Config previousConfig = Lizzie.config;
      LizzieFrame previousFrame = Lizzie.frame;
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      Field capabilityDiscovery = Leelaz.class.getDeclaredField("endGetCommandList");
      capabilityDiscovery.setAccessible(true);
      capabilityDiscovery.set(engine, true);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      Field outputStream = Leelaz.class.getDeclaredField("outputStream");
      outputStream.setAccessible(true);
      outputStream.set(engine, new BufferedOutputStream(output));
      Lizzie.leelaz = engine;
      Lizzie.board = null;
      Lizzie.config = allocate(Config.class);
      Lizzie.frame = allocate(LizzieFrame.class);
      return new TestState(
          previousEngine, previousBoard, previousConfig, previousFrame, engine, output);
    }

    TrackingAnalysisController.Context context() {
      return contextWithStones("stones");
    }

    TrackingAnalysisController.Context contextWithStones(String stones) {
      return new TrackingAnalysisController.Context(
          this,
          output,
          19,
          19,
          stones,
          true,
          "chinese",
          7.5,
          engine,
          engine.trackingStreamIncarnation(),
          new TrackingAnalysisController.Parameters(10, 100),
          null);
    }

    TrackingAnalysisController.Context contextVariant(String component) throws Exception {
      Object history = component.equals("history") ? new Object() : this;
      Object node = component.equals("node") ? new Object() : output;
      int width = component.equals("width") ? 13 : 19;
      int height = component.equals("height") ? 13 : 19;
      String stones = component.equals("stones") ? "other" : "stones";
      boolean blackToPlay = !component.equals("to-play");
      String rules = component.equals("rules") ? "japanese" : "chinese";
      double komi = component.equals("komi") ? 6.5 : 7.5;
      Leelaz contextEngine = component.equals("engine") ? new Leelaz("") : engine;
      long incarnation =
          component.equals("engine")
              ? contextEngine.trackingStreamIncarnation()
              : engine.trackingStreamIncarnation();
      TrackingAnalysisController.Parameters parameters =
          component.equals("parameters")
              ? new TrackingAnalysisController.Parameters(20, 200)
              : new TrackingAnalysisController.Parameters(10, 100);
      TrackingAnalysisController.ReadBoardContext readBoard =
          component.equals("readboard")
              ? new TrackingAnalysisController.ReadBoardContext(new Object(), 1L, new Object())
              : null;
      return new TrackingAnalysisController.Context(
          history,
          node,
          width,
          height,
          stones,
          blackToPlay,
          rules,
          komi,
          contextEngine,
          incarnation,
          parameters,
          readBoard);
    }

    String commands() {
      return output.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
      Lizzie.leelaz = previousEngine;
      Lizzie.board = previousBoard;
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
    }
  }

  private static final class RecordingPonderLeelaz extends Leelaz {
    private final AtomicInteger ponderCount = new AtomicInteger();

    private RecordingPonderLeelaz() throws Exception {
      super("");
    }

    @Override
    public void ponder() {
      ponderCount.incrementAndGet();
      Pondering();
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }
}
