package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

/** Builds the focused lightning-analysis menu used by the top toolbar. */
final class FlashAnalyzeMenu {
  static final String WHOLE_GAME_ACTION = "whole-game-lightning-overview";
  static final String PART_ACTION = "partial-lightning-analysis";
  static final String ALL_BRANCHES_ACTION = "all-branches-lightning-analysis";
  static final String SETTINGS_ACTION = "lightning-analysis-settings";

  private FlashAnalyzeMenu() {}

  static JPopupMenu create(ResourceBundle resources) {
    return create(
        resources,
        () -> Lizzie.frame.flashAnalyzeGame(true, false),
        () -> Lizzie.frame.flashAnalyzePart(),
        () -> Lizzie.frame.flashAnalyzeGame(false, true),
        () -> Lizzie.frame.flashAnalyzeSettings());
  }

  static JPopupMenu create(
      ResourceBundle resources,
      Runnable wholeGameAction,
      Runnable partAction,
      Runnable allBranchesAction,
      Runnable settingsAction) {
    JPopupMenu popup = new JPopupMenu();
    JFontMenuItem wholeGame =
        item(resources, "Menu.flashAnalyzeAllGame", WHOLE_GAME_ACTION, wholeGameAction);
    wholeGame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
    popup.add(wholeGame);
    popup.add(item(resources, "Menu.flashAnalyzePartGame", PART_ACTION, partAction));
    popup.add(
        item(resources, "Menu.flashAnalyzeAllBranches", ALL_BRANCHES_ACTION, allBranchesAction));
    popup.add(item(resources, "Menu.flashAnalyzeSettings", SETTINGS_ACTION, settingsAction));
    AppleStyleSupport.installPopupStyle(popup);
    return popup;
  }

  static void showBelow(JPopupMenu popup, JComponent invoker) {
    popup.show(invoker, 0, invoker.getHeight());
  }

  private static JFontMenuItem item(
      ResourceBundle resources, String key, String actionId, Runnable action) {
    JFontMenuItem item = new JFontMenuItem(resources.getString(key));
    item.putClientProperty(AutoAnalyzeMenu.ACTION_PROPERTY, actionId);
    item.addActionListener(event -> action.run());
    return item;
  }
}
