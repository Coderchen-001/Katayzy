package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class FlashAnalyzeMenuTest {
  @Test
  void topToolbarMenuContainsOnlyLightningAnalysisActions() {
    ResourceBundle resources =
        ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE);
    AtomicInteger activations = new AtomicInteger();

    JPopupMenu popup =
        FlashAnalyzeMenu.create(
            resources,
            activations::incrementAndGet,
            activations::incrementAndGet,
            activations::incrementAndGet,
            activations::incrementAndGet);

    assertEquals(4, popup.getComponentCount());
    assertEquals(resources.getString("Menu.flashAnalyzeAllGame"), item(popup, 0).getText());
    assertEquals(resources.getString("Menu.flashAnalyzePartGame"), item(popup, 1).getText());
    assertEquals(resources.getString("Menu.flashAnalyzeAllBranches"), item(popup, 2).getText());
    assertEquals(resources.getString("Menu.flashAnalyzeSettings"), item(popup, 3).getText());
    for (int index = 0; index < popup.getComponentCount(); index++) {
      item(popup, index).doClick();
    }
    assertEquals(4, activations.get());
  }

  @Test
  void popupIsAnchoredDirectlyBelowItsInvoker() {
    CapturingPopup popup = new CapturingPopup();
    JButton invoker = new JButton();
    invoker.setSize(42, 31);

    FlashAnalyzeMenu.showBelow(popup, invoker);

    assertSame(invoker, popup.invoker);
    assertEquals(0, popup.x);
    assertEquals(31, popup.y);
  }

  private static JMenuItem item(JPopupMenu popup, int index) {
    return (JMenuItem) popup.getComponent(index);
  }

  private static final class CapturingPopup extends JPopupMenu {
    private java.awt.Component invoker;
    private int x;
    private int y;

    @Override
    public void show(java.awt.Component invoker, int x, int y) {
      this.invoker = invoker;
      this.x = x;
      this.y = y;
    }
  }
}
