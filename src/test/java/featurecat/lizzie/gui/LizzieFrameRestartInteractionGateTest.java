package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LizzieFrameRestartInteractionGateTest {

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
}
