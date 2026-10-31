package featurecat.lizzie.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Custom popup renderer for the two toolbar menus.
 *
 * <p>The stock JPopupMenu/JMenuItem layout reserves icon, check and accelerator columns. That is
 * the source of the large blank area on the left and the fragile alignment. These rows are drawn
 * directly, so width, centering, hover and disabled colors are fully controlled here.
 */
final class ToolbarPopupSupport {
  private static final Color SURFACE = new Color(38, 41, 47);
  private static final Color TEXT = new Color(238, 241, 246);
  private static final Color MUTED = new Color(145, 151, 161);
  private static final Color HOVER = new Color(255, 255, 255, 26);
  private static final Color PRESSED = new Color(255, 255, 255, 48);
  private static final Color BORDER = new Color(255, 255, 255, 38);
  private static final Color DIVIDER = new Color(255, 255, 255, 28);
  private static final Font MENU_FONT = new Font("Microsoft YaHei", Font.PLAIN, 13);
  private static final FontMetrics MENU_METRICS =
      java.awt.Toolkit.getDefaultToolkit().getFontMetrics(MENU_FONT);

  private ToolbarPopupSupport() {}

  static void showAbove(JPopupMenu source, JComponent invoker) {
    if (source == null || invoker == null) return;

    List<Entry> entries = new ArrayList<>();
    int contentWidth = 160;
    for (Component component : source.getComponents()) {
      if (component instanceof JMenuItem) {
        JMenuItem item = (JMenuItem) component;
        String text = item.getText();
        if (text == null || text.trim().isEmpty()) continue;
        String shortcut = acceleratorText(item.getAccelerator());
        int width = MENU_METRICS.stringWidth(text);
        if (!shortcut.isEmpty()) {
          width += MENU_METRICS.stringWidth(shortcut) + 24;
        }
        contentWidth = Math.max(contentWidth, width + 24);
        entries.add(new Entry(item, text, shortcut, false));
      } else if (component instanceof JSeparator) {
        entries.add(new Entry(null, "", "", true));
      }
    }
    if (entries.isEmpty()) return;
    contentWidth = Math.min(contentWidth, 420);

    JPopupMenu menu = new JPopupMenu();
    menu.setOpaque(true);
    menu.setBackground(SURFACE);
    menu.setBorder(BorderFactory.createEmptyBorder());
    menu.setLayout(new BorderLayout());

    JPanel content = new JPanel();
    content.setOpaque(true);
    content.setBackground(SURFACE);
    content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

    for (Entry entry : entries) {
      if (entry.separator) {
        content.add(Box.createVerticalStrut(3));
        JComponent line = new Divider(contentWidth - 10);
        content.add(line);
        content.add(Box.createVerticalStrut(3));
      } else {
        content.add(new Row(entry, contentWidth - 10, menu));
      }
    }

    menu.add(content, BorderLayout.CENTER);
    int height = content.getPreferredSize().height;
    menu.setPopupSize(new Dimension(contentWidth, height));
    int x = (invoker.getWidth() - contentWidth) / 2;
    menu.show(invoker, x, -height - 2);
    SwingUtilities.invokeLater(menu::requestFocusInWindow);

    menu.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-menu");
    menu.getActionMap()
        .put(
            "close-menu",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                menu.setVisible(false);
              }
            });
  }

  private static String acceleratorText(KeyStroke accelerator) {
    if (accelerator == null) return "";
    int modifiers = accelerator.getModifiers();
    StringBuilder builder = new StringBuilder();
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) builder.append("Ctrl+");
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) builder.append("Shift+");
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) builder.append("Alt+");
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) builder.append("Meta+");
    int keyCode = accelerator.getKeyCode();
    String key = KeyEvent.getKeyText(keyCode);
    builder.append(key);
    return builder.toString();
  }

  private static final class Entry {
    final JMenuItem item;
    final String text;
    final String shortcut;
    final boolean separator;

    Entry(JMenuItem item, String text, String shortcut, boolean separator) {
      this.item = item;
      this.text = text;
      this.shortcut = shortcut;
      this.separator = separator;
    }
  }

  private static final class Row extends JComponent {
    private final Entry entry;
    private final int width;
    private final JPopupMenu menu;
    private boolean hovered;
    private boolean pressed;

    Row(Entry entry, int width, JPopupMenu menu) {
      this.entry = entry;
      this.width = Math.max(40, width);
      this.menu = menu;
      setOpaque(false);
      setPreferredSize(new Dimension(this.width, 30));
      setMinimumSize(new Dimension(this.width, 30));
      setMaximumSize(new Dimension(this.width, 30));
      setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
      setEnabled(entry.item.isEnabled());
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              if (isEnabled()) {
                hovered = true;
                repaint();
              }
            }

            @Override
            public void mouseExited(MouseEvent e) {
              hovered = false;
              pressed = false;
              repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
              if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
                pressed = true;
                repaint();
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              if (!isEnabled() || !SwingUtilities.isLeftMouseButton(e)) return;
              boolean inside = contains(e.getPoint());
              pressed = false;
              repaint();
              if (inside) {
                menu.setVisible(false);
                entry.item.doClick(0);
              }
            }
          });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (hovered || pressed) {
        g.setColor(pressed ? PRESSED : HOVER);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
      }
      g.setFont(MENU_FONT);
      FontMetrics fm = g.getFontMetrics();
      String text = entry.text;
      String shortcut = entry.shortcut;
      if (!shortcut.isEmpty()) text = text + "   " + shortcut;
      int textWidth = fm.stringWidth(text);
      int x = Math.max(8, (getWidth() - textWidth) / 2);
      int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
      g.setColor(isEnabled() ? TEXT : MUTED);
      g.drawString(text, x, y);
      g.dispose();
    }
  }

  private static final class Divider extends JComponent {
    Divider(int width) {
      setOpaque(false);
      setPreferredSize(new Dimension(Math.max(40, width), 1));
      setMinimumSize(new Dimension(Math.max(40, width), 1));
      setMaximumSize(new Dimension(Math.max(40, width), 1));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      graphics.setColor(DIVIDER);
      graphics.drawLine(6, 0, getWidth() - 6, 0);
    }
  }
}
