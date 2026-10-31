package featurecat.lizzie.ui2;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

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
    return capture(frame, frame == null ? null : frame.getJMenuBar());
  }

  public static String capture(JFrame frame, JMenuBar extraMenuBar) {
    if (frame == null) return "";
    StringBuilder out = new StringBuilder();
    JMenuBar menuBar = frame.getJMenuBar();
    if (menuBar != null) {
      capture(menuBar, "JMenuBar", out, 0);
    }
    // Lizzie uses a WindowMenuStrip for painting, but the JMenuBar model still owns
    // all 12 menus and their children. Capture it explicitly so menu regressions are visible.
    if (extraMenuBar != null && extraMenuBar != menuBar) {
      capture(extraMenuBar, "ModelJMenuBar", out, 0);
    }
    capture(frame.getContentPane(), "content", out, 0);
    return out.toString();
  }

  public static void write(JFrame frame, Path path) throws IOException {
    write(frame, frame == null ? null : frame.getJMenuBar(), path);
  }

  public static void write(JFrame frame, JMenuBar extraMenuBar, Path path) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    Files.writeString(path, capture(frame, extraMenuBar), StandardCharsets.UTF_8);
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
        .append(" | mnemonic=")
        .append(mnemonicOf(component))
        .append(" | accelerator=")
        .append(acceleratorOf(component))
        .append(" | selected=")
        .append(selectedOf(component))
        .append(" | visible=")
        .append(component.isVisible())
        .append(" | enabled=")
        .append(component.isEnabled())
        .append('\n');
    if (component instanceof Container) {
      Container container = (Container) component;
      List<Component> children = new ArrayList<>();
      for (Component child : container.getComponents()) children.add(child);
      Map<String, Integer> used = new HashMap<>();
      for (Component child : children) {
        String base = childSegment(child);
        int count = used.getOrDefault(base, 0);
        used.put(base, count + 1);
        String segment = count == 0 ? base : base + "#" + count;
        capture(child, path + "/" + segment, out, depth + 1);
      }
    }
  }

  private static String childSegment(Component component) {
    String name = component.getName();
    if (name != null && !name.isBlank()) {
      return name.replace('/', '_').replace('\\', '_');
    }
    return component.getClass().getSimpleName();
  }

  private static String mnemonicOf(Component component) {
    if (!(component instanceof AbstractButton)) return "";
    int mnemonic = ((AbstractButton) component).getMnemonic();
    if (mnemonic <= 0) return "";
    return String.valueOf((char) mnemonic);
  }

  private static String acceleratorOf(Component component) {
    if (!(component instanceof JMenuItem)) return "";
    KeyStroke accelerator = ((JMenuItem) component).getAccelerator();
    return accelerator == null ? "" : accelerator.toString();
  }

  private static String selectedOf(Component component) {
    if (!(component instanceof AbstractButton)) return "";
    AbstractButton button = (AbstractButton) component;
    boolean toggle =
        button instanceof JCheckBoxMenuItem
            || button instanceof JRadioButtonMenuItem
            || button instanceof JToggleButton;
    return toggle ? String.valueOf(button.isSelected()) : "";
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
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        + "\"";
  }
}
