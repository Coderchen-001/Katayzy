package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WholeGameAnalysisSessionTest {
  private static final int BOARD_SIZE = 3;

  @Test
  void engineFailureTerminatesInsteadOfReusingAPotentiallyDirtyTransport() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      fixture.engine.requestCount = 1;
      setField(fixture.session, "state", WholeGameAnalysisSession.State.BASELINE);
      setField(fixture.session, "engine", fixture.engine);
      setField(fixture.session, "activeDispatchGeneration", 1);

      invokeEngineFailure(fixture.session, 1);

      assertEquals(WholeGameAnalysisSession.State.FAILED, fixture.session.state());
      assertEquals(1, fixture.engine.requestCount);
      assertTrue(fixture.engine.shutdownRequested);
      assertTrue(fixture.engine.callbacksCleared);
      assertTrue(fixture.engine.quitCalled.await(2, TimeUnit.SECONDS));
      drainEdt();
    }
  }

  @Test
  void staleEngineFailureCannotTerminateANewerDispatch() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      setField(fixture.session, "state", WholeGameAnalysisSession.State.DEEP);
      setField(fixture.session, "engine", fixture.engine);
      setField(fixture.session, "activeDispatchGeneration", 2);

      invokeEngineFailure(fixture.session, 1);

      assertEquals(WholeGameAnalysisSession.State.DEEP, fixture.session.state());
      assertFalse(fixture.engine.shutdownRequested);
      assertFalse(fixture.engine.callbacksCleared);
    }
  }

  @Test
  void cancelMarksTheEngineShutdownBeforeTheAsyncCloserRuns() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      setField(fixture.session, "state", WholeGameAnalysisSession.State.BASELINE);
      setField(fixture.session, "engine", fixture.engine);
      setField(fixture.session, "activeDispatchGeneration", 1);

      fixture.session.cancel();

      assertEquals(WholeGameAnalysisSession.State.CANCELLED, fixture.session.state());
      assertTrue(fixture.engine.shutdownRequested);
      assertTrue(fixture.engine.callbacksCleared);
      assertTrue(fixture.engine.quitCalled.await(2, TimeUnit.SECONDS));
      drainEdt();
    }
  }

  @Test
  void pauseClosesTheActiveEngineAndKeepsTheSessionResumable() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      setField(fixture.session, "state", WholeGameAnalysisSession.State.BASELINE);
      setField(fixture.session, "engine", fixture.engine);
      setField(fixture.session, "activeDispatchGeneration", 1);

      fixture.session.pause();

      assertTrue(fixture.engine.shutdownRequested);
      assertTrue(fixture.engine.callbacksCleared);
      assertTrue(fixture.engine.quitCalled.await(2, TimeUnit.SECONDS));
      waitForState(fixture.session, WholeGameAnalysisSession.State.PAUSED);
      assertTrue(fixture.session.isActive());
      assertTrue(fixture.session.isPaused());
      assertFalse(fixture.session.isTerminal());
    }
  }

  @Test
  void pauseWhilePreparingInvalidatesThePendingEngineStart() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      setField(fixture.session, "state", WholeGameAnalysisSession.State.PREPARING);
      setField(fixture.session, "engineStartGeneration", 3);

      fixture.session.pause();

      assertEquals(WholeGameAnalysisSession.State.PAUSED, fixture.session.state());
      assertEquals(4, getIntField(fixture.session, "engineStartGeneration"));
    }
  }

  @Test
  void engineCreatedBeforePauseCannotAttachAfterTheSessionIsPaused() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();
      setField(fixture.session, "state", WholeGameAnalysisSession.State.PREPARING);
      setField(fixture.session, "engineStartGeneration", 3);

      fixture.session.pause();
      invokeAcceptEngine(
          fixture.session, fixture.engine, 3, WholeGameAnalysisSession.State.BASELINE);

      assertTrue(fixture.engine.quitCalled.await(2, TimeUnit.SECONDS));
      assertEquals(WholeGameAnalysisSession.State.PAUSED, fixture.session.state());
      assertFalse(fixture.session.isTerminal());
    }
  }

  @Test
  void resumeCreatesAFreshEngineAndContinuesTheInterruptedStage() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      AtomicInteger creations = new AtomicInteger();
      SessionFixture fixture =
          SessionFixture.createWithFactory(
              () -> {
                creations.incrementAndGet();
                return SessionFixture.newEngine();
              });
      setField(fixture.session, "state", WholeGameAnalysisSession.State.PAUSED);
      setField(fixture.session, "resumeStage", WholeGameAnalysisSession.State.BASELINE);

      fixture.session.resume();

      waitForState(fixture.session, WholeGameAnalysisSession.State.BASELINE);
      assertEquals(1, creations.get());
      assertTrue(fixture.session.isActive());
      fixture.session.cancel();
      drainEdt();
    }
  }

  @Test
  void resumedDeepStageDispatchesOnlyPositionsWithoutCompletedResults() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      BoardHistoryList history = new BoardHistoryList(BoardData.empty(BOARD_SIZE, BOARD_SIZE));
      BoardHistoryNode root = history.getStart();
      BoardHistoryNode first = root.add(new BoardHistoryNode(passData(Stone.BLACK, 1)));
      BoardHistoryNode second = first.add(new BoardHistoryNode(passData(Stone.WHITE, 2)));
      installCompleteAnalysis(root.getData(), 500);
      installCompleteAnalysis(first.getData(), 500);
      Board board = allocate(Board.class);
      board.setHistory(history);
      Lizzie.board = board;
      TrackingFrame frame = allocate(TrackingFrame.class);
      Lizzie.frame = frame;
      WholeGameAnalysisPlan plan = WholeGameAnalysisPlan.create(root, 32, 500);
      SessionAnalysisEngine resumedEngine = SessionFixture.newEngine();
      WholeGameAnalysisSession session =
          new WholeGameAnalysisSession(frame, plan, snapshot -> {}, () -> resumedEngine);
      setField(session, "state", WholeGameAnalysisSession.State.PAUSED);
      setField(session, "resumeStage", WholeGameAnalysisSession.State.DEEP);

      session.resume();

      waitForState(session, WholeGameAnalysisSession.State.DEEP);
      waitForRequest(resumedEngine);
      assertEquals(List.of(second), resumedEngine.requestedNodes);
      assertEquals(500, resumedEngine.requestedVisits);
      session.cancel();
      drainEdt();
    }
  }

  @Test
  void competitiveTrackingDoesNotDelayWholeGameRequestDispatch() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      Lizzie.config.analysisReuseCurrentEngine = true;
      SessionAnalysisEngine engine = SessionFixture.newEngine();
      CountDownLatch engineCreated = new CountDownLatch(1);
      SessionFixture fixture =
          SessionFixture.createWithFactory(
              () -> {
                engineCreated.countDown();
                return engine;
              });
      Leelaz previousLeelaz = Lizzie.leelaz;
      CompetitiveTrackingLeelaz foreground = new CompetitiveTrackingLeelaz();
      Lizzie.leelaz = foreground;

      try {
        fixture.session.start();

        assertTrue(
            foreground.previewed.await(2, TimeUnit.SECONDS),
            "whole-game session did not inspect the competitive tracking owner");
        assertTrue(
            engineCreated.await(2, TimeUnit.SECONDS),
            "whole-game session waited for competitive tracking to end");
        waitForState(fixture.session, WholeGameAnalysisSession.State.BASELINE);
        waitForRequest(engine);
      } finally {
        fixture.session.cancel();
        engine.quitCalled.await(2, TimeUnit.SECONDS);
        Lizzie.leelaz = previousLeelaz;
        drainEdt();
      }
      assertEquals(0L, engine.quitCalled.getCount(), "whole-game analysis closer did not finish");
    }
  }

  @Test
  void engineSwapCannotSplitExclusiveAndPreviewAcrossInstances() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      Lizzie.config.analysisReuseCurrentEngine = true;
      SessionAnalysisEngine engine = SessionFixture.newEngine();
      CountDownLatch engineCreated = new CountDownLatch(1);
      SessionFixture fixture =
          SessionFixture.createWithFactory(
              () -> {
                engineCreated.countDown();
                return engine;
              });
      Leelaz previousLeelaz = Lizzie.leelaz;
      CompetitiveTrackingLeelaz replacement = new CompetitiveTrackingLeelaz();
      CompetitiveTrackingLeelaz foreground = new CompetitiveTrackingLeelaz(replacement);
      Lizzie.leelaz = foreground;

      try {
        fixture.session.start();

        assertTrue(
            foreground.previewed.await(2, TimeUnit.SECONDS),
            "whole-game session split one availability check across two engine instances");
        assertTrue(
            replacement.previewed.await(2, TimeUnit.SECONDS),
            "whole-game session used an availability result from a replaced engine");
        assertTrue(engineCreated.await(2, TimeUnit.SECONDS));
      } finally {
        fixture.session.cancel();
        engine.quitCalled.await(2, TimeUnit.SECONDS);
        Lizzie.leelaz = previousLeelaz;
        drainEdt();
      }
      assertEquals(0L, engine.quitCalled.getCount(), "whole-game analysis closer did not finish");
    }
  }

  @Test
  void komiChangeInvalidatesTheSessionSemanticSnapshot() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();

      Lizzie.board
          .getHistory()
          .getGameInfo()
          .setKomi(Lizzie.board.getHistory().getGameInfo().getKomi() + 0.5);

      assertFalse(invokeCurrentGameMatches(fixture.session));
    }
  }

  @Test
  void analysisRulesChangeInvalidatesTheSessionSemanticSnapshot() throws Exception {
    try (TestEnvironment env = TestEnvironment.open()) {
      SessionFixture fixture = SessionFixture.create();

      Lizzie.config.analysisSpecificRules = "{\"scoringRule\":\"AREA\"}";

      assertFalse(invokeCurrentGameMatches(fixture.session));
    }
  }

  private static void invokeEngineFailure(WholeGameAnalysisSession session, int generation)
      throws Exception {
    Method method = WholeGameAnalysisSession.class.getDeclaredMethod("onEngineFailure", int.class);
    method.setAccessible(true);
    method.invoke(session, generation);
  }

  private static boolean invokeCurrentGameMatches(WholeGameAnalysisSession session)
      throws Exception {
    Method method = WholeGameAnalysisSession.class.getDeclaredMethod("currentGameMatches");
    method.setAccessible(true);
    return (boolean) method.invoke(session);
  }

  private static void invokeAcceptEngine(
      WholeGameAnalysisSession session,
      AnalysisEngine engine,
      int generation,
      WholeGameAnalysisSession.State stage)
      throws Exception {
    Method method =
        WholeGameAnalysisSession.class.getDeclaredMethod(
            "acceptEngine", AnalysisEngine.class, int.class, WholeGameAnalysisSession.State.class);
    method.setAccessible(true);
    method.invoke(session, engine, generation, stage);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = WholeGameAnalysisSession.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static int getIntField(Object target, String name) throws Exception {
    Field field = WholeGameAnalysisSession.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.getInt(target);
  }

  private static void waitForState(
      WholeGameAnalysisSession session, WholeGameAnalysisSession.State expected) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      drainEdt();
      if (session.state() == expected) {
        return;
      }
      Thread.sleep(10L);
    }
    assertEquals(expected, session.state());
  }

  private static void waitForRequest(SessionAnalysisEngine engine) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      if (engine.requestedNodes != null) {
        return;
      }
      Thread.sleep(10L);
    }
    assertTrue(engine.requestedNodes != null, "Expected a whole-game request");
  }

  private static BoardData passData(Stone color, int moveNumber) {
    Stone[] stones = new Stone[BOARD_SIZE * BOARD_SIZE];
    Arrays.fill(stones, Stone.EMPTY);
    return BoardData.pass(
        stones,
        color,
        color == Stone.WHITE,
        new Zobrist(),
        moveNumber,
        new int[BOARD_SIZE * BOARD_SIZE],
        0,
        0,
        50.0,
        0);
  }

  private static void installCompleteAnalysis(BoardData data, int visits) {
    MoveData move = new MoveData();
    move.coordinate = "B2";
    move.playouts = visits;
    move.winrate = 50.0;
    move.order = 0;
    move.variation = List.of("B2");
    data.setPlayouts(visits);
    data.bestMoves = List.of(move);
  }

  private static void drainEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static final class SessionFixture {
    private final WholeGameAnalysisSession session;
    private final SessionAnalysisEngine engine;

    private SessionFixture(WholeGameAnalysisSession session, SessionAnalysisEngine engine) {
      this.session = session;
      this.engine = engine;
    }

    private static SessionFixture create() throws Exception {
      return createWithFactory(null);
    }

    private static SessionFixture createWithFactory(WholeGameAnalysisSession.EngineFactory factory)
        throws Exception {
      BoardHistoryList history = new BoardHistoryList(BoardData.empty(BOARD_SIZE, BOARD_SIZE));
      Board board = allocate(Board.class);
      board.setHistory(history);
      Lizzie.board = board;
      TrackingFrame frame = allocate(TrackingFrame.class);
      Lizzie.frame = frame;
      WholeGameAnalysisPlan plan = WholeGameAnalysisPlan.create(history.getStart(), 32, 500);
      SessionAnalysisEngine engine = newEngine();
      WholeGameAnalysisSession session =
          factory == null
              ? new WholeGameAnalysisSession(frame, plan, snapshot -> {})
              : new WholeGameAnalysisSession(frame, plan, snapshot -> {}, factory);
      return new SessionFixture(session, engine);
    }

    private static SessionAnalysisEngine newEngine() {
      try {
        SessionAnalysisEngine engine = allocate(SessionAnalysisEngine.class);
        engine.quitCalled = new CountDownLatch(1);
        return engine;
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  private static final class SessionAnalysisEngine extends AnalysisEngine {
    private int requestCount;
    private boolean shutdownRequested;
    private boolean callbacksCleared;
    private CountDownLatch quitCalled;
    private volatile List<BoardHistoryNode> requestedNodes;
    private volatile int requestedVisits;

    private SessionAnalysisEngine() throws IOException {
      super(true);
    }

    @Override
    void requestShutdown() {
      shutdownRequested = true;
    }

    @Override
    public void clearRequestCallbacks() {
      callbacksCleared = true;
    }

    @Override
    public void normalQuit() {
      quitCalled.countDown();
    }

    @Override
    public boolean isLoaded() {
      return true;
    }

    @Override
    public int startWholeGameRequest(
        List<BoardHistoryNode> requestedNodes, int targetVisits, boolean includeOwnership) {
      requestCount++;
      this.requestedNodes = List.copyOf(requestedNodes);
      requestedVisits = targetVisits;
      return requestedNodes.size();
    }
  }

  private static final class CompetitiveTrackingLeelaz extends Leelaz {
    private final CountDownLatch previewed = new CountDownLatch(1);
    private final Leelaz replacementOnBusyCheck;

    private CompetitiveTrackingLeelaz() throws IOException {
      this(null);
    }

    private CompetitiveTrackingLeelaz(Leelaz replacementOnBusyCheck) throws IOException {
      super("");
      this.replacementOnBusyCheck = replacementOnBusyCheck;
    }

    @Override
    public boolean hasExclusiveGtpWorkInProgress() {
      if (replacementOnBusyCheck != null) {
        Lizzie.leelaz = replacementOnBusyCheck;
      }
      return true;
    }

    @Override
    public ExclusiveGtpLeaseAvailability previewForegroundAnalysisLeaseAvailability() {
      previewed.countDown();
      return ExclusiveGtpLeaseAvailability.AVAILABLE;
    }
  }

  private static final class TrackingFrame extends LizzieFrame {
    private TrackingFrame() {}

    @Override
    public void onWholeGameAnalysisFinished(
        WholeGameAnalysisSession session,
        AnalysisEngine completedEngine,
        boolean resumeForegroundAnalysis) {}

    @Override
    public void attachWholeGameAnalysisEngine(
        WholeGameAnalysisSession session, AnalysisEngine engine) {}
  }

  private static final class TestEnvironment implements AutoCloseable {
    private final int previousBoardWidth;
    private final int previousBoardHeight;
    private final Config previousConfig;
    private final Board previousBoard;
    private final LizzieFrame previousFrame;

    private TestEnvironment(
        int previousBoardWidth,
        int previousBoardHeight,
        Config previousConfig,
        Board previousBoard,
        LizzieFrame previousFrame) {
      this.previousBoardWidth = previousBoardWidth;
      this.previousBoardHeight = previousBoardHeight;
      this.previousConfig = previousConfig;
      this.previousBoard = previousBoard;
      this.previousFrame = previousFrame;
    }

    private static TestEnvironment open() {
      TestEnvironment environment =
          new TestEnvironment(
              Board.boardWidth, Board.boardHeight, Lizzie.config, Lizzie.board, Lizzie.frame);
      Board.boardWidth = BOARD_SIZE;
      Board.boardHeight = BOARD_SIZE;
      Zobrist.init();
      Config config;
      try {
        config = allocate(Config.class);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
      config.analysisUseCurrentRules = false;
      config.analysisSpecificRules = "";
      config.currentKataGoRules = "";
      config.autoLoadKataRules = false;
      config.kataRules = "";
      Lizzie.config = config;
      return environment;
    }

    @Override
    public void close() {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.config = previousConfig;
      Lizzie.board = previousBoard;
      Lizzie.frame = previousFrame;
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
