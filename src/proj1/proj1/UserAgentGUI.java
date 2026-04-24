package proj1.proj1;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class UserAgentGUI extends JFrame {
    public DefaultListModel<String> gameListModel = new DefaultListModel<>();
    public JList<String> gameList = new JList<>(gameListModel);
    public JScrollPane gameListPane;

    public JPanel visualHandPanel = new JPanel();
    private JScrollPane handScrollPane;
    public JPanel battlefieldPanel = new JPanel();

    public JButton refreshButton = new JButton("🔍 SCAN FOR LOBBIES");
    public JButton joinButton = new JButton("🚪 JOIN SELECTED GAME");
    public JButton doneButton = new JButton("👌 PASS / DONE");

    private UserAgent myAgent;

    public JLabel trumpLabel = new JLabel("Trump: ?");
    public JLabel deckLabel = new JLabel("Deck: 36");
    public JLabel turnLabel = new JLabel("TURN: Wait...");

    public UserAgentGUI(UserAgent agent) {
        this.myAgent = agent;
        setTitle("Agent Casino: Ultra Visual Edition");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        // --- 1. Left Panel (Persistent Lobby List) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        TitledBorder lobbyBorder = BorderFactory.createTitledBorder("AVAILABLE LOBBIES");
        lobbyBorder.setTitleFont(new Font("Arial", Font.BOLD, 14));
        leftPanel.setBorder(lobbyBorder);

        gameList.setFont(new Font("Arial", Font.PLAIN, 14));
        gameListPane = new JScrollPane(gameList);
        leftPanel.add(gameListPane, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(180, 0));

        // --- 2. Battlefield (Large Middle Table) ---
        JPanel battlefieldContainer = new JPanel(new BorderLayout());
        battlefieldContainer.setBackground(new Color(0, 80, 0));
        battlefieldContainer.setBorder(BorderFactory.createTitledBorder(null, "TABLE", 0, 0, null, Color.WHITE));

        // --- 3. Info Panel (Top Right Corner Overlay) ---
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        trumpLabel.setForeground(Color.WHITE);
        trumpLabel.setFont(new Font("Arial", Font.BOLD, 14));
        deckLabel.setForeground(Color.WHITE);
        deckLabel.setFont(new Font("Arial", Font.BOLD, 14));
        turnLabel.setForeground(Color.YELLOW);
        turnLabel.setFont(new Font("Arial", Font.BOLD, 16));

        infoPanel.add(trumpLabel);
        infoPanel.add(deckLabel);
        infoPanel.add(turnLabel);

        // Use a wrapper to push infoPanel to the Top-Right
        JPanel rightAligner = new JPanel(new BorderLayout());
        rightAligner.setOpaque(false);
        rightAligner.add(infoPanel, BorderLayout.EAST);

        battlefieldContainer.add(rightAligner, BorderLayout.NORTH);

        // Center Area for Cards
        battlefieldPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        battlefieldPanel.setOpaque(false);
        battlefieldContainer.add(battlefieldPanel, BorderLayout.CENTER);

        // --- 4. South Panel (User Hand & Controls) ---
        visualHandPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        visualHandPanel.setBackground(new Color(0, 100, 0));

        handScrollPane = new JScrollPane(visualHandPanel);
        handScrollPane.setPreferredSize(new Dimension(0, 200));
        TitledBorder handBorder = BorderFactory.createTitledBorder("YOUR HAND");
        handBorder.setTitleColor(Color.WHITE);
        handScrollPane.setBorder(handBorder);

        // Button Row
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        buttonPanel.setPreferredSize(new Dimension(0, 50));
        buttonPanel.add(refreshButton);
        buttonPanel.add(joinButton);
        buttonPanel.add(doneButton);
        doneButton.setVisible(false); // Hidden until in game

        // Final Layout Assembly
        JPanel centerArea = new JPanel(new BorderLayout());
        centerArea.add(battlefieldContainer, BorderLayout.CENTER);

        JPanel southArea = new JPanel(new BorderLayout());
        southArea.add(handScrollPane, BorderLayout.CENTER);
        southArea.add(buttonPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
        add(centerArea, BorderLayout.CENTER);
        add(southArea, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
    }

    public void setInGameUI(boolean inGame) {
        SwingUtilities.invokeLater(() -> {
            if (inGame) {
                refreshButton.setText("✋ TAKE CARDS");
                joinButton.setEnabled(false);
                doneButton.setVisible(true);
            } else {
                refreshButton.setText("🔍 SCAN FOR LOBBIES");
                joinButton.setEnabled(true);
                doneButton.setVisible(false);
                clearTable();
                clearHand();
            }
            revalidate();
            repaint();
        });
    }

    public void setTrumpUI(String suit, String icon) {
        SwingUtilities.invokeLater(() -> trumpLabel.setText("Trump: " + icon + " " + suit));
    }

    public void setDeckUI(int count) {
        SwingUtilities.invokeLater(() -> deckLabel.setText("Deck: " + count));
    }

    public void setTurnUI(boolean myTurn) {
        SwingUtilities.invokeLater(() -> {
            if (myTurn) {
                turnLabel.setText("TURN: ATTACK");
                turnLabel.setForeground(Color.GREEN);
            } else {
                turnLabel.setText("TURN: DEFEND");
                turnLabel.setForeground(Color.RED);
            }
        });
    }

    public void addVisualCardToHand(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            visualHandPanel.add(new VisualCard(rank, suit, myAgent));
            visualHandPanel.revalidate();
            visualHandPanel.repaint();
        });
    }

    public void clearHand() {
        SwingUtilities.invokeLater(() -> {
            visualHandPanel.removeAll();
            visualHandPanel.revalidate();
            visualHandPanel.repaint();
        });
    }

    public void removeVisualCard(VisualCard cardComponent) {
        SwingUtilities.invokeLater(() -> {
            visualHandPanel.remove(cardComponent);
            visualHandPanel.revalidate();
            visualHandPanel.repaint();
        });
    }

    // Kept for logic compatibility but no longer updates UI
    public void updateLog(String msg) {
        System.out.println("LOG: " + msg);
    }

    public void addGame(String name) {
        SwingUtilities.invokeLater(() -> gameListModel.addElement(name));
    }

    public String getSelectedGame() {
        return gameList.getSelectedValue();
    }

    public void clearTable() {
        SwingUtilities.invokeLater(() -> {
            battlefieldPanel.removeAll();
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void addServerDefenseToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            VisualCard def = new VisualCard(rank, suit, null);
            def.setBackground(Color.LIGHT_GRAY);
            battlefieldPanel.add(def);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void addUserDefenseToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            VisualCard def = new VisualCard(rank, suit, myAgent);
            battlefieldPanel.add(def);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void addServerAttackToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            VisualCard atkCard = new VisualCard(rank, suit, null);
            atkCard.setBackground(Color.LIGHT_GRAY);
            battlefieldPanel.add(atkCard);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void addUserAttackToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            VisualCard card = new VisualCard(rank, suit, myAgent);
            for (java.awt.event.MouseListener ml : card.getMouseListeners()) {
                card.removeMouseListener(ml);
            }
            battlefieldPanel.add(card);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void prepareGameUI(String gameName) {
        setInGameUI(true);
    }

    public void displayEndGameMessage(String text, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            battlefieldPanel.removeAll();
            JLabel winLabel = new JLabel("<html><div style='text-align: center;'>" + text + "</div></html>", SwingConstants.CENTER);
            winLabel.setFont(new Font("Serif", Font.BOLD, 60));
            winLabel.setForeground(textColor);
            battlefieldPanel.add(winLabel);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }
}