package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ExtraMode;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.EngineManager;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.ReadBoard;
import featurecat.lizzie.analysis.ReadBoardTrackingEligibilityAdapter;
import featurecat.lizzie.analysis.TrackingAnalysisController;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackingProductionCutoverTest {
  @Test
  void localAndStableReadBoardEntriesUseTheSameCurrentEngineController() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      assertTrue(environment.commands().contains("800000000 stop\n"));
      TrackingAnalysisController controller = frame.trackingAnalysisController();
      assertSame(controller, frame.trackingAnalysisController());

      controller.clear();
      environment.completeInitialFence(800000000);
      environment.installStableReadBoard();

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("B2"));
      assertSame(controller, frame.trackingAnalysisController());
      assertTrue(
          controller.snapshot().context().readBoardContext().isPresent(),
          "stable ReadBoard entry must bind its accepted frame identity to the same controller");
      assertTrue(environment.commands().contains("800000001 stop\n"));
    }
  }

  @Test
  void productionEntryRemovesPendingAndClearsCurrentImmediately() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("B2"));

      assertTrue(frame.removeTrackingPoint("B2"));
      assertEquals(List.of("A1"), List.copyOf(frame.trackingDisplaySnapshot().selectedPoints()));

      frame.clearTrackingPoints();

      assertTrue(frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertTrue(environment.commands().contains("800000002 stop\n"));
    }
  }

  @Test
  void rendererGateSuppressesStaleNodeAndPonderInvalidationDoesNotReacquire() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      TrackingAnalysisController.DisplaySnapshot original = frame.trackingDisplaySnapshot();
      assertTrue(frame.isTrackingDisplayCurrent(original));

      Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));

      assertFalse(frame.isTrackingDisplayCurrent(original));
      frame.onMainEnginePonder();

      assertTrue(frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertTrue(environment.commands().contains("800000002 stop\n"));
      assertFalse(environment.commands().contains("800000003 stop\n"));
    }
  }

  @Test
  void trackingDisplayChangesRequestRefreshWithoutOrdinaryParserRepaint() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      TrackingFrame frame = (TrackingFrame) environment.frame;
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      int beforeInfo = frame.analysisRefreshRequests;

      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 1.5 pv A1");

      assertTrue(frame.analysisRefreshRequests > beforeInfo);
      int beforeCompletion = frame.analysisRefreshRequests;
      environment.completeFinalFence(800000002);
      assertTrue(frame.analysisRefreshRequests > beforeCompletion);
    }
  }

  @Test
  void trackingOverlayIsIndependentOfCandidateVisibilityAndSuppressedForBranchOrStaleNode()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      TrackingFrame frame = (TrackingFrame) environment.frame;
      Font previousWinrateFont = LizzieFrame.winrateFont;
      Font previousPlayoutsFont = LizzieFrame.playoutsFont;
      LizzieFrame.winrateFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      LizzieFrame.playoutsFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      try {
        assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
        environment.completeInitialFence(800000000);
        environment.sendTrackingInfo("info move A1 visits 10 winrate 0.51 scoreLead 1.5 pv A1");
        Lizzie.config.showBestMoves = false;
        frame.isShowingHeatmap = true;
        frame.isShowingPolicy = true;
        BoardRenderer renderer = configuredRenderer();

        assertTrue(hasVisiblePaint(renderTrackingOverlay(renderer)));

        setField(renderer, BoardRenderer.class, "isShowingBranch", true);
        assertFalse(hasVisiblePaint(renderTrackingOverlay(renderer)));
        setField(renderer, BoardRenderer.class, "isShowingBranch", false);
        Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
        assertFalse(hasVisiblePaint(renderTrackingOverlay(renderer)));
      } finally {
        LizzieFrame.winrateFont = previousWinrateFont;
        LizzieFrame.playoutsFont = previousPlayoutsFont;
      }
    }
  }

  @Test
  void unsupportedEngineModesAndMissingCapabilitiesAreHiddenBeforeLeaseAcquisition()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.engine.useJavaSSH = true;
      assertFalse(environment.frame.canStartTrackingAnalysis());

      environment.engine.useJavaSSH = false;
      Lizzie.config.extraMode = ExtraMode.Double_Engine;
      assertFalse(environment.frame.canStartTrackingAnalysis());

      Lizzie.config.extraMode = ExtraMode.Normal;
      environment.engine.commandLists.remove("kata-analyze");
      assertFalse(environment.frame.canStartTrackingAnalysis());

      environment.engine.commandLists.add("kata-analyze");
      setField(environment.engine, Leelaz.class, "outputStream", null);
      assertFalse(environment.frame.canStartTrackingAnalysis());
    }
  }

  private static BoardRenderer configuredRenderer() throws Exception {
    BoardRenderer renderer = new BoardRenderer(false);
    setField(renderer, BoardRenderer.class, "x", 0);
    setField(renderer, BoardRenderer.class, "y", 0);
    setField(renderer, BoardRenderer.class, "scaledMarginWidth", 20);
    setField(renderer, BoardRenderer.class, "scaledMarginHeight", 20);
    setField(renderer, BoardRenderer.class, "squareWidth", 40);
    setField(renderer, BoardRenderer.class, "squareHeight", 40);
    setField(renderer, BoardRenderer.class, "stoneRadius", 16);
    return renderer;
  }

  private static BufferedImage renderTrackingOverlay(BoardRenderer renderer) throws Exception {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      Method method =
          BoardRenderer.class.getDeclaredMethod("drawTrackingOverlay", Graphics2D.class);
      method.setAccessible(true);
      method.invoke(renderer, graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static boolean hasVisiblePaint(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static void closeExclusiveSessionForTest(Leelaz engine) throws Exception {
    Field field = Leelaz.class.getDeclaredField("exclusiveGtpSession");
    field.setAccessible(true);
    Object session = field.get(engine);
    if (session == null) {
      return;
    }
    Method cancelInitial =
        Leelaz.class.getDeclaredMethod("cancelExclusiveGtpInitialStopTimeout", session.getClass());
    cancelInitial.setAccessible(true);
    cancelInitial.invoke(engine, session);
    Method cancelRelease =
        Leelaz.class.getDeclaredMethod("cancelExclusiveGtpReleaseStopTimeout", session.getClass());
    cancelRelease.setAccessible(true);
    cancelRelease.invoke(engine, session);
    Method close = Leelaz.class.getDeclaredMethod("closeExclusiveGtpSession", session.getClass());
    close.setAccessible(true);
    close.invoke(engine, session);
  }

  private static final class TestEnvironment implements AutoCloseable {
    private final Leelaz previousEngine;
    private final Board previousBoard;
    private final Config previousConfig;
    private final LizzieFrame previousFrame;
    private final boolean previousEmpty;
    private final boolean previousEngineGame;
    private final int previousWidth;
    private final int previousHeight;
    private final Leelaz engine;
    private final ByteArrayOutputStream output;
    private final LizzieFrame frame;

    private TestEnvironment(
        Leelaz previousEngine,
        Board previousBoard,
        Config previousConfig,
        LizzieFrame previousFrame,
        boolean previousEmpty,
        boolean previousEngineGame,
        int previousWidth,
        int previousHeight,
        Leelaz engine,
        ByteArrayOutputStream output,
        LizzieFrame frame) {
      this.previousEngine = previousEngine;
      this.previousBoard = previousBoard;
      this.previousConfig = previousConfig;
      this.previousFrame = previousFrame;
      this.previousEmpty = previousEmpty;
      this.previousEngineGame = previousEngineGame;
      this.previousWidth = previousWidth;
      this.previousHeight = previousHeight;
      this.engine = engine;
      this.output = output;
      this.frame = frame;
    }

    static TestEnvironment open() throws Exception {
      Leelaz previousEngine = Lizzie.leelaz;
      Board previousBoard = Lizzie.board;
      Config previousConfig = Lizzie.config;
      LizzieFrame previousFrame = Lizzie.frame;
      boolean previousEmpty = EngineManager.isEmpty;
      boolean previousEngineGame = EngineManager.isEngineGame;
      int previousWidth = Board.boardWidth;
      int previousHeight = Board.boardHeight;

      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Board board = allocate(Board.class);
      board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
      Config config = allocate(Config.class);
      config.analyzeUpdateIntervalCentisec = 10;
      config.trackingAnalysisMaxVisits = 100;
      config.currentKataGoRules = "chinese";
      Leelaz engine = new Leelaz("");
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      setField(engine, Leelaz.class, "endGetCommandList", true);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      setField(engine, Leelaz.class, "outputStream", new BufferedOutputStream(output));
      LizzieFrame frame = allocate(TrackingFrame.class);

      EngineManager.isEmpty = false;
      EngineManager.isEngineGame = false;
      Lizzie.board = board;
      Lizzie.config = config;
      Lizzie.leelaz = engine;
      Lizzie.frame = frame;
      return new TestEnvironment(
          previousEngine,
          previousBoard,
          previousConfig,
          previousFrame,
          previousEmpty,
          previousEngineGame,
          previousWidth,
          previousHeight,
          engine,
          output,
          frame);
    }

    void installStableReadBoard() throws Exception {
      ReadBoard readBoard = allocate(ReadBoard.class);
      BoardHistoryNode node = Lizzie.board.getHistory().getCurrentHistoryNode();
      setField(readBoard, ReadBoard.class, "trackingEligibilityIdentity", new Object());
      setField(readBoard, ReadBoard.class, "trackingEligibilityRevision", 7L);
      setField(readBoard, ReadBoard.class, "trackingEligibilityNode", node);
      setField(
          readBoard,
          ReadBoard.class,
          "trackingEligibilityBoardRevision",
          Lizzie.board.getContextRevision());
      setField(
          readBoard,
          ReadBoard.class,
          "trackingEligibilityReason",
          ReadBoardTrackingEligibilityAdapter.Reason.STABLE);
      frame.readBoard = readBoard;
    }

    void completeInitialFence(int id) throws Exception {
      dispatch("=" + id);
      processCommandResponse("=" + id);
      dispatch("");
    }

    void completeFinalFence(int id) throws Exception {
      dispatch("");
      dispatch("=" + id);
      dispatch("");
    }

    void sendTrackingInfo(String line) throws Exception {
      dispatch(line);
    }

    private void dispatch(String line) throws Exception {
      java.lang.reflect.Method method =
          Leelaz.class.getDeclaredMethod("dispatchExclusiveGtpLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    private void processCommandResponse(String line) throws Exception {
      java.lang.reflect.Method method =
          Leelaz.class.getDeclaredMethod("processCommandResponseLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    String commands() {
      return output.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws Exception {
      closeExclusiveSessionForTest(engine);
      Lizzie.leelaz = previousEngine;
      Lizzie.board = previousBoard;
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      EngineManager.isEmpty = previousEmpty;
      EngineManager.isEngineGame = previousEngineGame;
      Board.boardWidth = previousWidth;
      Board.boardHeight = previousHeight;
    }
  }

  private static final class TrackingFrame extends LizzieFrame {
    private int analysisRefreshRequests;

    @Override
    public void refresh() {}

    @Override
    public void requestAnalysisRefresh() {
      analysisRefreshRequests++;
    }
  }

  private static void setField(Object target, Class<?> owner, String name, Object value)
      throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }
}
