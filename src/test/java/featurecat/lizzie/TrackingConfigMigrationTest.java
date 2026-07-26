package featurecat.lizzie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class TrackingConfigMigrationTest {
  @Test
  void legacySecondProcessSettingsMigrateToSingleStreamVisitsOnly() {
    JSONObject ui =
        new JSONObject()
            .put("tracking-engine-preload", true)
            .put("tracking-engine-skip-warning", true)
            .put("tracking-engine-max-visits", 321);

    int visits = Config.migrateTrackingAnalysisConfig(ui);

    assertEquals(321, visits);
    assertEquals(321, ui.getInt("tracking-analysis-max-visits"));
    assertFalse(ui.has("tracking-engine-preload"));
    assertFalse(ui.has("tracking-engine-skip-warning"));
    assertFalse(ui.has("tracking-engine-max-visits"));
  }

  @Test
  void currentSingleStreamVisitsWinOverLegacyValue() {
    JSONObject ui =
        new JSONObject()
            .put("tracking-analysis-max-visits", 456)
            .put("tracking-engine-max-visits", 123);

    assertEquals(456, Config.migrateTrackingAnalysisConfig(ui));
    assertEquals(456, ui.getInt("tracking-analysis-max-visits"));
    assertFalse(ui.has("tracking-engine-max-visits"));
  }
}
