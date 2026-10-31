package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Compact engine/status footer that lives at the bottom of the sidebar glass card. */
public class SidebarStatusPanel extends JPanel {
  private static final int HEIGHT = 46;

  public SidebarStatusPanel() {
    setOpaque(false);
    setPreferredSize(new Dimension(0, HEIGHT));
    setMinimumSize(new Dimension(0, HEIGHT));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int width = getWidth();
    int height = getHeight();
    g.setColor(new Color(255, 255, 255, 22));
    g.drawLine(0, 0, width, 0);

    boolean loaded = Lizzie.leelaz != null && Lizzie.leelaz.isLoaded();
    String title =
        loaded && Lizzie.leelaz.oriEnginename != null && !Lizzie.leelaz.oriEnginename.isBlank()
            ? Lizzie.leelaz.oriEnginename
            : "未加载引擎";

    Font titleFont = new Font(Lizzie.config.uiFontName, Font.PLAIN, 13);
    Font detailFont = new Font(Lizzie.config.uiFontName, Font.PLAIN, 12);
    g.setFont(titleFont);
    FontMetrics titleFm = g.getFontMetrics();
    title = ellipsize(title, titleFm, Math.max(20, width - 34));

    g.setColor(new Color(235, 239, 244));
    g.drawString(title, 26, 18);

    Color accent =
        Lizzie.config != null && Lizzie.config.theme != null
            ? Lizzie.config.theme.glassAccentColor()
            : new Color(96, 165, 250);
    g.setColor(loaded ? accent : new Color(150, 156, 164));
    g.fillOval(12, 10, 7, 7);

    g.setFont(detailFont);
    g.setColor(new Color(168, 176, 186));
    String detail;
    if (loaded) {
      detail = (Lizzie.leelaz.isPondering() ? "分析中" : "已暂停") + " · 空格继续";
      if (!Lizzie.config.userKnownX) detail += " · X 隐藏提示";
    } else {
      detail = "点击引擎菜单加载";
    }
    FontMetrics detailFm = g.getFontMetrics();
    detail = ellipsize(detail, detailFm, Math.max(20, width - 26));
    g.drawString(detail, 26, 36);
    g.dispose();
  }

  private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
    if (text == null || text.isEmpty() || fm.stringWidth(text) <= maxWidth) return text;
    String ellipsis = "...";
    int ellipsisWidth = fm.stringWidth(ellipsis);
    if (ellipsisWidth >= maxWidth) return "";
    int lo = 0;
    int hi = text.length();
    while (lo < hi) {
      int mid = (lo + hi + 1) >>> 1;
      if (fm.stringWidth(text.substring(0, mid)) + ellipsisWidth <= maxWidth) lo = mid;
      else hi = mid - 1;
    }
    return text.substring(0, lo) + ellipsis;
  }
}
