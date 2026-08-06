package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.HumanSlAnalysisRunner;
import featurecat.lizzie.util.Utils;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Lets the player configure a casual HumanSL human-vs-AI game (rank, handicap, color, time). */
public final class NewHumanSlGameDialog extends JDialog {
  private static final long serialVersionUID = 1L;

  private static final String[] RANK_PROFILES = buildRankProfiles();

  private final ResourceBundle resourceBundle = Lizzie.resourceBundle;
  private final JFontComboBox<String> rankBox = new JFontComboBox<String>();
  private final JFontComboBox<String> colorBox = new JFontComboBox<String>();
  private final JFontComboBox<Integer> handicapBox = new JFontComboBox<Integer>();
  private final JFontTextField komiField = new JFontTextField();
  private final JFontComboBox<String> timeBox = new JFontComboBox<String>();
  private boolean cancelled = true;

  public NewHumanSlGameDialog(Window owner) {
    super(owner);
    setTitle(resourceBundle.getString("HumanSlGame.dialog.title"));
    setModal(true);
    try {
      setIconImage(ImageIO.read(MoreEngines.class.getResourceAsStream("/assets/logo.png")));
    } catch (IOException ignored) {
    }
    setResizable(false);
    initComponents();
    pack();
    setLocationRelativeTo(owner);
  }

  private void initComponents() {
    JPanel content = new JPanel(new GridLayout(0, 2, 8, 8));
    content.setBorder(new EmptyBorder(12, 12, 12, 12));

    for (String profile : RANK_PROFILES) {
      rankBox.addItem(profile.replace("rank_", "").toUpperCase(java.util.Locale.ROOT));
    }
    rankBox.setSelectedItem("3K");

    colorBox.addItem(resourceBundle.getString("NewGameDialog.playBlack"));
    colorBox.addItem(resourceBundle.getString("NewGameDialog.playWhite"));
    colorBox.setSelectedIndex(0);

    for (int i = 0; i <= 9; i++) {
      handicapBox.addItem(i);
    }
    handicapBox.setSelectedItem(0);
    handicapBox.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updateKomiForHandicap();
          }
        });

    komiField.setDocument(new KomiDocument(true));
    komiField.setText("7.5");

    timeBox.addItem("5");
    timeBox.addItem("10");
    timeBox.addItem("20");
    timeBox.addItem("30");
    timeBox.addItem("60");
    timeBox.setSelectedItem("10");

    content.add(new JFontLabel(resourceBundle.getString("HumanSlGame.dialog.rank")));
    content.add(rankBox);
    content.add(new JFontLabel(resourceBundle.getString("HumanSlGame.dialog.color")));
    content.add(colorBox);
    content.add(new JFontLabel(resourceBundle.getString("HumanSlGame.dialog.handicap")));
    content.add(handicapBox);
    content.add(new JFontLabel(resourceBundle.getString("HumanSlGame.dialog.komi")));
    content.add(komiField);
    content.add(new JFontLabel(resourceBundle.getString("HumanSlGame.dialog.time")));
    content.add(timeBox);

    JPanel buttons = new JPanel();
    JFontButton ok = new JFontButton(resourceBundle.getString("NewAnaGameDialog.okButton"));
    ok.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            onConfirm();
          }
        });
    JFontButton cancel = new JFontButton(resourceBundle.getString("LizzieFrame.cancel"));
    cancel.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            cancelled = true;
            setVisible(false);
          }
        });
    buttons.add(ok);
    buttons.add(cancel);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(content, BorderLayout.CENTER);
    getContentPane().add(buttons, BorderLayout.SOUTH);
    setMinimumSize(new Dimension(360, 260));
  }

  private void updateKomiForHandicap() {
    int handicap =
        handicapBox.getSelectedItem() == null ? 0 : (Integer) handicapBox.getSelectedItem();
    komiField.setText(handicap >= 2 ? "0" : "7.5");
  }

  private void onConfirm() {
    Path modelPath = HumanSlGameController.resolveDefaultHumanModel();
    if (modelPath == null) {
      JOptionPane.showMessageDialog(
          this,
          "未找到 human 模型文件，请确认整合包 human-sl-models 目录下包含模型文件。",
          resourceBundle.getString("HumanSlGame.dialog.title"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    startConfiguredGame(modelPath);
  }

  private boolean startConfiguredGame(Path modelPath) {
    String command = resolveAnalysisCommand();
    if (command.trim().isEmpty()) {
      Utils.showMsg(resourceBundle.getString("HumanSlGame.error.noEngine"));
      return false;
    }
    HumanSlAnalysisRunner runner = new HumanSlAnalysisRunner(command, modelPath);
    if (!runner.start()) {
      Utils.showMsg(
          java.text.MessageFormat.format(
              resourceBundle.getString("HumanSlGame.error.startFailed"),
              runner.getUnavailableReason()));
      try {
        runner.close();
      } catch (Exception ignored) {
      }
      return false;
    }

    String profile = RANK_PROFILES[Math.max(0, rankBox.getSelectedIndex())];
    boolean humanIsBlack = colorBox.getSelectedIndex() == 0;
    int handicap =
        handicapBox.getSelectedItem() == null ? 0 : (Integer) handicapBox.getSelectedItem();
    double komi = Utils.parseTextToDouble(komiField, handicap >= 2 ? 0.0 : 7.5);
    int timeSeconds = 10;
    try {
      timeSeconds = Integer.parseInt((String) timeBox.getSelectedItem());
    } catch (NumberFormatException ignored) {
    }

    HumanSlGameController controller =
        new HumanSlGameController(runner, profile, humanIsBlack, handicap, komi, timeSeconds);
    cancelled = false;
    setVisible(false);
    controller.start();
    return true;
  }

  private String resolveAnalysisCommand() {
    if (Lizzie.config == null) {
      return "";
    }
    String command = Lizzie.config.analysisEngineCommand;
    return command == null ? "" : command;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  private static String[] buildRankProfiles() {
    java.util.List<String> profiles = new java.util.ArrayList<String>();
    for (int rank = 19; rank >= 1; rank--) {
      profiles.add("rank_" + rank + "k");
    }
    for (int rank = 1; rank <= 9; rank++) {
      profiles.add("rank_" + rank + "d");
    }
    return profiles.toArray(new String[0]);
  }
}
