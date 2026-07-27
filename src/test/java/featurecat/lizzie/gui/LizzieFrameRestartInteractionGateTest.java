package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LizzieFrameRestartInteractionGateTest {

  @Test
  void gateBlocksRealMouseMutationUntilItCloses() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame frame = new JFrame();
    JButton mutation = new JButton("mutate");
    AtomicInteger mutationCount = new AtomicInteger();
    mutation.addActionListener(event -> mutationCount.incrementAndGet());
    Robot robot = new Robot();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            frame.add(mutation);
            frame.pack();
            frame.setLocation(100, 100);
            frame.setVisible(true);
          });
      robot.waitForIdle();
      Point buttonLocation = mutation.getLocationOnScreen();
      int x = buttonLocation.x + mutation.getWidth() / 2;
      int y = buttonLocation.y + mutation.getHeight() / 2;

      LizzieFrame.RestartInteractionGate gate = LizzieFrame.beginRestartInteractionGate(frame);
      click(robot, x, y);
      assertTrue(mutationCount.get() == 0);

      gate.close();
      click(robot, x, y);
      assertTrue(mutationCount.get() == 1);
    } finally {
      SwingUtilities.invokeAndWait(frame::dispose);
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
}
