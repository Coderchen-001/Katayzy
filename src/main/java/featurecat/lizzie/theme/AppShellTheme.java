package featurecat.lizzie.theme;

import java.awt.Color;

/**
 * App-level shell colors. Kept separate from Sabaki board themes so switching a board texture
 * cannot silently change the sidebar/toolbar palette.
 */
public final class AppShellTheme {
  public static final String GRAPHITE = "graphite";
  public static final String WARM = "warm";
  public static final String SLATE = "slate";

  private AppShellTheme() {}

  public static String normalize(String value) {
    if (WARM.equalsIgnoreCase(value)) return WARM;
    if (SLATE.equalsIgnoreCase(value)) return SLATE;
    return GRAPHITE;
  }

  public static String label(String value) {
    switch (normalize(value)) {
      case WARM:
        return "暖灰";
      case SLATE:
        return "墨青";
      default:
        return "石墨黑";
    }
  }

  public static Color overlay(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(40, 34, 29, 190);
      case SLATE:
        return new Color(24, 33, 36, 190);
      default:
        return new Color(22, 26, 32, 190);
    }
  }

  public static Color border(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(255, 238, 215, 40);
      case SLATE:
        return new Color(200, 238, 235, 38);
      default:
        return new Color(255, 255, 255, 36);
    }
  }

  public static Color highlight(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(255, 238, 215, 80);
      case SLATE:
        return new Color(200, 238, 235, 76);
      default:
        return new Color(255, 255, 255, 78);
    }
  }

  public static Color shadow(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(12, 8, 5, 96);
      case SLATE:
        return new Color(4, 14, 16, 96);
      default:
        return new Color(0, 0, 0, 96);
    }
  }

  public static Color accent(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(214, 160, 90);
      case SLATE:
        return new Color(112, 190, 178);
      default:
        return new Color(96, 165, 250);
    }
  }

  public static Color toolbarBackground(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(44, 38, 32, 236);
      case SLATE:
        return new Color(24, 35, 38, 236);
      default:
        return new Color(24, 26, 31, 236);
    }
  }

  public static Color toolbarText(String value) {
    return new Color(238, 241, 246);
  }

  public static Color popupBackground(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(54, 46, 39);
      case SLATE:
        return new Color(32, 46, 48);
      default:
        return new Color(38, 41, 47);
    }
  }

  public static Color dialogBackground(String value) {
    switch (normalize(value)) {
      case WARM:
        return new Color(48, 42, 36);
      case SLATE:
        return new Color(28, 42, 44);
      default:
        return new Color(30, 33, 38);
    }
  }
}
