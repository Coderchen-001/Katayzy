package featurecat.lizzie.ui2;

import featurecat.lizzie.gui.LizzieFrame;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Opt-in UI2 dump/smoke entry point. The normal launch path returns immediately when no
 * --ui2-dump/--ui2-smoke argument is present.
 */
public final class Ui2Harness {
  private static boolean enabled;
  private static boolean smoke;
  private static Path dumpPath;
  private static int settleMillis = 1500;

  private Ui2Harness() {}

  public static String[] filterArgs(String[] args) {
    if (args == null) return new String[0];
    List<String> remaining = new ArrayList<>();
    for (String arg : args) {
      if (arg == null) continue;
      if (arg.equals("--ui2-smoke")) {
        enabled = true;
        smoke = true;
      } else if (arg.startsWith("--ui2-dump=")) {
        enabled = true;
        dumpPath = Path.of(arg.substring("--ui2-dump=".length()));
      } else if (arg.startsWith("--ui2-delay=")) {
        try {
          settleMillis = Math.max(200, Integer.parseInt(arg.substring("--ui2-delay=".length())));
        } catch (NumberFormatException ignored) {
          // keep the default settle time
        }
      } else {
        remaining.add(arg);
      }
    }
    if (enabled && dumpPath == null) dumpPath = Path.of("ui2-smoke.txt");
    return remaining.toArray(new String[0]);
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void afterFrameReady(JFrame frame) {
    if (!enabled || frame == null) return;
    SwingUtilities.invokeLater(
        () ->
            new Timer(
                    settleMillis,
                    event -> {
                      ((Timer) event.getSource()).stop();
                      try {
                        Ui2Snapshot.write(frame, LizzieFrame.menu, dumpPath);
                        System.out.println("ui2 dump written: " + dumpPath.toAbsolutePath());
                        if (smoke) {
                          frame.dispose();
                          System.exit(0);
                        }
                      } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(2);
                      }
                    })
                .start());
  }
}
