package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.WholeGameAnalysisSession;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import org.junit.jupiter.api.Test;

class WholeGameAnalysisDialogTest {
  @Test
  void remainingDurationUsesLocaleNeutralClockNotation() {
    assertEquals("00:00", WholeGameAnalysisDialog.formatDuration(0));
    assertEquals("00:01", WholeGameAnalysisDialog.formatDuration(1));
    assertEquals("01:05", WholeGameAnalysisDialog.formatDuration(65_000));
    assertEquals("1:01:01", WholeGameAnalysisDialog.formatDuration(3_661_000));
  }

  @Test
  void completionButtonUsesUiThatPaintsItsAccentBackgroundOnWindows() {
    JButton button = new JButton("Close");

    WholeGameAnalysisDialog.installPortableButtonFill(button);

    assertTrue(button.getUI() instanceof BasicButtonUI);
    assertTrue(button.isContentAreaFilled());
  }

  @Test
  void controlsExposeStartPauseResumeAndStopAsDistinctStates() {
    WholeGameAnalysisDialog.ControlState idle =
        WholeGameAnalysisDialog.controlState(WholeGameAnalysisSession.State.IDLE);
    WholeGameAnalysisDialog.ControlState running =
        WholeGameAnalysisDialog.controlState(WholeGameAnalysisSession.State.BASELINE);
    WholeGameAnalysisDialog.ControlState pausing =
        WholeGameAnalysisDialog.controlState(WholeGameAnalysisSession.State.PAUSING);
    WholeGameAnalysisDialog.ControlState paused =
        WholeGameAnalysisDialog.controlState(WholeGameAnalysisSession.State.PAUSED);
    WholeGameAnalysisDialog.ControlState stopped =
        WholeGameAnalysisDialog.controlState(WholeGameAnalysisSession.State.CANCELLED);

    assertTrue(idle.startEnabled);
    assertFalse(idle.pauseEnabled);
    assertFalse(idle.stopEnabled);
    assertFalse(running.startEnabled);
    assertTrue(running.pauseEnabled);
    assertTrue(running.stopEnabled);
    assertFalse(pausing.pauseEnabled);
    assertTrue(pausing.stopEnabled);
    assertTrue(paused.pauseEnabled);
    assertTrue(paused.resumeLabel);
    assertTrue(stopped.stopEnabled);
    assertTrue(stopped.closeLabel);
  }

  @Test
  void packedDialogIsOnlyClampedWhenTheUsableScreenIsSmaller() {
    assertEquals(
        new Dimension(640, 430),
        WholeGameAnalysisDialog.clampDialogSize(new Dimension(640, 430), new Dimension(1200, 800)));
    assertEquals(
        new Dimension(560, 360),
        WholeGameAnalysisDialog.clampDialogSize(new Dimension(640, 430), new Dimension(560, 360)));
  }
}
