package featurecat.lizzie.ui2;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * Deterministic component-tree snapshot for the UI2 golden baseline.
 *
 * <p>This deliberately captures structure and visible state, not pixels or object identity. Volatile
 * values (engine names, clocks) must be normalized by the caller's test config before using the
 * output as a golden file.
 */
public final class Ui2Snapshot {
  private Ui2Snapshot() {}

  public static String capture(JFrame frame) {
    if (frame == null) return "";
    StringBuilder out = new StringBuilder();
    JMenuBar menuBar = frame.getJMenuBar();
    if (menuBar != null) {
      capture(menuBar, "JMenuBar", out, 0);
    }
    capture(frame.getContentPane(), "content", out, 0);
    return out.toString();
  }

  public static void write(JFrame frame, Path path) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    Files.writeString(path, capture(frame), StandardCharsets.UTF_8);
  }

  private static void capture(Component component, String path, StringBuilder out, int depth) {
    if (component == null) return;
    String indent = "  ".repeat(depth);
    out.append(indent)
        .append(path)
        .append(" | ")
        .append(component.getClass().getSimpleName())
        .append(" | text=")
        .append(quote(textOf(component)))
        .append(" | visible=")
        .append(component.isVisible())
        .append(" | enabled=")
        .append(component.isEnabled())
        .append(" | bounds=")
        .append(component.getX())
        .append(',')
        .append(component.getY())
        .append(',')
        .append(component.getWidth())
        .append(',')
        .append(component.getHeight())
        .append('\n');
    if (component instanceof Container) {
      Container container = (Container) component;
      List<Component> children = new ArrayList<>();
      for (Component child : container.getComponents()) children.add(child);
      for (int i = 0; i < children.size(); i++) {
        capture(children.get(i), path + "/" + i, out, depth + 1);
      }
    }
  }

  private static String textOf(Component component) {
    if (component instanceof AbstractButton) {
      AbstractButton button = (AbstractButton) component;
      String text = button.getText();
      if (text != null && !text.isEmpty()) return text;
    }
    if (component instanceof JLabel) return ((JLabel) component).getText();
    if (component instanceof JMenu) return ((JMenu) component).getText();
    if (component instanceof JMenuItem) return ((JMenuItem) component).getText();
    return "";
  }

  private static String quote(String value) {
    if (value == null) return "\"\"";
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
