package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.analysis.Leelaz;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class DesktopTimeControlTest {
  @Test
  void freshConfigurationUsesFixedTwoSecondMoveTime() throws Exception {
    Config config =
        ConfigTestHelper.createForTests(Files.createTempDirectory("lizzie-time-default"));

    assertFalse(config.advanceTimeSettings);
    assertFalse(config.kataTimeSettings);
    assertEquals(2, config.maxGameThinkingTimeSeconds);
  }

  @Test
  void humanTimeModeChangesOnlyWhenSelectionIsCommitted() throws Exception {
    Config config =
        ConfigTestHelper.createForTests(Files.createTempDirectory("lizzie-time-transaction"));
    config.uiConfig = new JSONObject();

    DesktopTimeControl.Mode draft = DesktopTimeControl.Mode.KATAGO_ADVANCED;
    assertFalse(config.advanceTimeSettings);
    assertFalse(config.kataTimeSettings);
    assertFalse(config.uiConfig.has("advance-time-settings"));
    assertFalse(config.uiConfig.has("kata-time-settings"));

    DesktopTimeControl.commitHumanSelection(config, draft, 2);

    assertFalse(config.advanceTimeSettings);
    assertTrue(config.kataTimeSettings);
    assertEquals(2, config.kataTimeType);
    assertFalse(config.uiConfig.getBoolean("advance-time-settings"));
    assertTrue(config.uiConfig.getBoolean("kata-time-settings"));
    assertEquals(2, config.uiConfig.getInt("kata-time-type"));
  }

  @Test
  void engineGameAdvancedModeChangesOnlyWhenSelectionIsCommitted() throws Exception {
    Config config =
        ConfigTestHelper.createForTests(Files.createTempDirectory("lizzie-pk-time-transaction"));
    config.uiConfig = new JSONObject();

    boolean draftAdvanced = true;
    assertFalse(config.pkAdvanceTimeSettings);
    assertFalse(config.uiConfig.has("pk-advance-time-settings"));

    DesktopTimeControl.commitEngineGameSelection(config, draftAdvanced);

    assertTrue(config.pkAdvanceTimeSettings);
    assertTrue(config.uiConfig.getBoolean("pk-advance-time-settings"));
  }

  @Test
  void localAndSshEngineGameFixedTimeKeepStandardGtpCommand() throws Exception {
    RecordingLeelaz local = new RecordingLeelaz("katago gtp");
    RecordingLeelaz ssh = new RecordingLeelaz("ssh host katago gtp");

    DesktopTimeControl.sendEngineGameFixedTime(local, 4);
    DesktopTimeControl.sendEngineGameFixedTime(ssh, 5);

    assertEquals(List.of("time_settings 0 4 1"), local.commands);
    assertEquals(List.of("time_settings 0 5 1"), ssh.commands);
  }

  private static final class RecordingLeelaz extends Leelaz {
    private final List<String> commands = new ArrayList<>();

    private RecordingLeelaz(String command) throws Exception {
      super(command);
    }

    @Override
    public void sendCommand(String command) {
      commands.add(command);
    }
  }
}
