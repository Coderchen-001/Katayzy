package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LizzieFrameRestartInteractionGateTest {

  @Test
  void gateBlocksDesktopBoardShortcutMenuFileAndOwnedDialogMutations() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    JPanel board = new JPanel();
    AtomicInteger boardMutations = new AtomicInteger();
    AtomicInteger navigationMutations = new AtomicInteger();
    AtomicInteger menuMutations = new AtomicInteger();
    AtomicInteger fileOpenMutations = new AtomicInteger();
    AtomicInteger dragDropMutations = new AtomicInteger();
    AtomicInteger dialogMutations = new AtomicInteger();
    board.setFocusable(true);
    board.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            boardMutations.incrementAndGet();
            board.requestFocusInWindow();
          }
        });
    board
        .getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "navigate");
    board
        .getActionMap()
        .put(
            "navigate",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                navigationMutations.incrementAndGet();
              }
            });
    board.setTransferHandler(
        new TransferHandler() {
          @Override
          public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
          }

          @Override
          public boolean importData(TransferSupport support) {
            dragDropMutations.incrementAndGet();
            return true;
          }
        });
    TransferHandler dragDropEntry = board.getTransferHandler();
    JMenuBar menuBar = new JMenuBar();
    JMenu menu = new JMenu("actions");
    JMenuItem menuAction = new JMenuItem("menu");
    menuAction.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
    menuAction.addActionListener(event -> menuMutations.incrementAndGet());
    JMenuItem fileOpen = new JMenuItem("open");
    fileOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    fileOpen.addActionListener(event -> fileOpenMutations.incrementAndGet());
    menu.add(menuAction);
    menu.add(fileOpen);
    menuBar.add(menu);
    List<JDialog> settingsDialogs = new ArrayList<>();
    List<JButton> settingsMutations = new ArrayList<>();
    for (String title : List.of("board-size", "komi", "rules")) {
      JDialog dialog = new JDialog(frame, title);
      JButton mutation = new JButton("apply");
      mutation.addActionListener(event -> dialogMutations.incrementAndGet());
      dialog.add(mutation);
      settingsDialogs.add(dialog);
      settingsMutations.add(mutation);
    }
    Robot robot = new Robot();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setJMenuBar(menuBar);
            frame.add(board);
            board.setPreferredSize(new java.awt.Dimension(240, 160));
            frame.pack();
            frame.setLocation(100, 100);
            frame.setVisible(true);
            int dialogX = 380;
            for (JDialog dialog : settingsDialogs) {
              dialog.pack();
              dialog.setLocation(dialogX, 100);
              dialog.setVisible(true);
              dialogX += 120;
            }
          });
      robot.waitForIdle();
      Point boardLocation = board.getLocationOnScreen();
      int boardX = boardLocation.x + board.getWidth() / 2;
      int boardY = boardLocation.y + board.getHeight() / 2;
      click(robot, boardX, boardY);
      focus(frame, board);
      robot.waitForIdle();
      boardMutations.set(0);

      LizzieFrame.RestartInteractionGate gate = LizzieFrame.beginRestartInteractionGate(frame);
      exerciseMainWindowInputs(robot, boardX, boardY);
      exerciseDialogInputs(robot, settingsMutations);
      assertTrue(boardMutations.get() == 0);
      assertTrue(navigationMutations.get() == 0);
      assertTrue(menuMutations.get() == 0);
      assertTrue(fileOpenMutations.get() == 0);
      assertNull(board.getTransferHandler());
      assertTrue(dragDropMutations.get() == 0);
      assertTrue(dialogMutations.get() == 0);

      SwingUtilities.invokeAndWait(
          () -> {
            settingsDialogs.forEach(dialog -> dialog.setVisible(false));
          });
      gate.close();
      focus(frame, board);
      robot.waitForIdle();
      exerciseMainWindowInputs(robot, boardX, boardY);
      assertTrue(boardMutations.get() == 1);
      assertTrue(navigationMutations.get() == 1);
      assertTrue(menuMutations.get() == 1);
      assertTrue(fileOpenMutations.get() == 1);
      assertSame(dragDropEntry, board.getTransferHandler());
      assertTrue(
          board
              .getTransferHandler()
              .importData(
                  new TransferHandler.TransferSupport(board, new StringSelection("sgf-file"))));
      assertTrue(dragDropMutations.get() == 1);
      SwingUtilities.invokeAndWait(
          () -> settingsDialogs.forEach(dialog -> dialog.setVisible(true)));
      robot.waitForIdle();
      exerciseDialogInputs(robot, settingsMutations);
      assertTrue(dialogMutations.get() == 3);
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            for (JDialog dialog : settingsDialogs) {
              dialog.dispose();
            }
            frame.dispose();
          });
    }
  }

  @Test
  void gateDisablesExistingOwnedWindowsAndRestoresOriginalStateExactlyOnce() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    JDialog enabledDialog = new JDialog(frame);
    JDialog disabledDialog = new JDialog(frame);
    JDialog nestedDialog = new JDialog(enabledDialog);
    List<Boolean> mutationsOnEdt = new ArrayList<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setEnabled(true);
            enabledDialog.setEnabled(true);
            disabledDialog.setEnabled(false);
            nestedDialog.setEnabled(true);
            frame.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            enabledDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            disabledDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
            nestedDialog.addPropertyChangeListener(
                "enabled", event -> mutationsOnEdt.add(SwingUtilities.isEventDispatchThread()));
          });

      LizzieFrame.RestartInteractionGate gate = LizzieFrame.beginRestartInteractionGate(frame);

      assertFalse(frame.isEnabled());
      assertFalse(enabledDialog.isEnabled());
      assertFalse(disabledDialog.isEnabled());
      assertFalse(nestedDialog.isEnabled());

      gate.close();
      gate.close();

      assertTrue(frame.isEnabled());
      assertTrue(enabledDialog.isEnabled());
      assertFalse(disabledDialog.isEnabled());
      assertTrue(nestedDialog.isEnabled());
      assertTrue(mutationsOnEdt.stream().allMatch(Boolean::booleanValue));
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            nestedDialog.dispose();
            disabledDialog.dispose();
            enabledDialog.dispose();
            frame.dispose();
          });
    }
  }

  @Test
  void gateRollsBackAlreadyDisabledWindowsWhenAnOwnedWindowFails() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    FailingDisableDialog dialog = new FailingDisableDialog(frame);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.setEnabled(true);
            dialog.setEnabled(true);
          });

      assertThrows(
          IllegalStateException.class, () -> LizzieFrame.beginRestartInteractionGate(frame));

      assertTrue(frame.isEnabled());
      assertTrue(dialog.isEnabled());
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            dialog.dispose();
            frame.dispose();
          });
    }
  }

  private static final class FailingDisableDialog extends JDialog {
    private boolean failNextDisable = true;

    private FailingDisableDialog(JFrame owner) {
      super(owner);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (!enabled && failNextDisable) {
        failNextDisable = false;
        throw new IllegalStateException("controlled gate failure");
      }
    }
  }

  private static void click(Robot robot, int x, int y) {
    robot.mouseMove(x, y);
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    robot.waitForIdle();
  }

  private static void exerciseMainWindowInputs(Robot robot, int boardX, int boardY) {
    click(robot, boardX, boardY);
    robot.keyPress(KeyEvent.VK_RIGHT);
    robot.keyRelease(KeyEvent.VK_RIGHT);
    pressShortcut(robot, KeyEvent.VK_M);
    pressShortcut(robot, KeyEvent.VK_O);
    robot.waitForIdle();
  }

  private static void exerciseDialogInputs(Robot robot, List<JButton> settingsMutations) {
    for (JButton mutation : settingsMutations) {
      Point location = mutation.getLocationOnScreen();
      click(robot, location.x + mutation.getWidth() / 2, location.y + mutation.getHeight() / 2);
    }
    robot.waitForIdle();
  }

  private static void pressShortcut(Robot robot, int keyCode) {
    robot.keyPress(KeyEvent.VK_CONTROL);
    robot.keyPress(keyCode);
    robot.keyRelease(keyCode);
    robot.keyRelease(KeyEvent.VK_CONTROL);
  }

  private static void focus(JFrame frame, JPanel board) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          frame.toFront();
          frame.requestFocus();
          board.requestFocusInWindow();
        });
  }
}
