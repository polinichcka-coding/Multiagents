package proj1.proj1;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class UserAgentGUI extends JFrame {
    public DefaultListModel<String> gameListModel = new DefaultListModel<>();
    public JList<String> gameList = new JList<>(gameListModel);

    public JPanel visualHandPanel = new JPanel();
    private JScrollPane handScrollPane;
    public JPanel battlefieldPanel = new JPanel();

    public JButton refreshButton = new JButton("🔍 SCAN FOR LOBBIES");
    public JButton joinButton = new JButton("🚪 JOIN SELECTED GAME");

    private JTextArea logArea = new JTextArea();
    private UserAgent myAgent;

    public JPanel opponentPanel = new JPanel();
    public JLabel trumpLabel = new JLabel("Trump: ?");

    // NEW: Extra status labels for your "Turn" and "Deck" info
    public JLabel deckLabel = new JLabel("Deck: 36");
    public JLabel turnLabel = new JLabel("TURN: Wait...");
    public JButton doneButton = new JButton("Pass"); // Новая кнопка

    public UserAgentGUI(UserAgent agent) {
        this.myAgent = agent;
        setTitle("Agent Casino: Ultra Visual Edition");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        // --- 1. Left Panel (Lobby List) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        TitledBorder lobbyBorder = BorderFactory.createTitledBorder("AVAILABLE LOBBIES");
        lobbyBorder.setTitleFont(new Font("Arial", Font.BOLD, 14));
        leftPanel.setBorder(lobbyBorder);

        gameList.setFont(new Font("Arial", Font.PLAIN, 14));
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftPanel.add(new JScrollPane(gameList), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(140, 0));

        // --- 2. Center Panel (Log / Table Container) ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        TitledBorder logBorder = BorderFactory.createTitledBorder("GAME ENGINE LOG");
        logBorder.setTitleFont(new Font("Arial", Font.BOLD, 14));
        centerPanel.setBorder(logBorder);

        logArea.setEditable(false);
        logArea.setBackground(new Color(10, 10, 10));
        logArea.setForeground(new Color(50, 255, 50));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // --- 3. Bottom Panel (Your Visual Hand) ---
        visualHandPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        visualHandPanel.setBackground(new Color(0, 100, 0));

        handScrollPane = new JScrollPane(visualHandPanel);
        handScrollPane.setPreferredSize(new Dimension(0, 180));
        TitledBorder handBorder = BorderFactory.createTitledBorder("YOUR HAND");
        handScrollPane.setBorder(handBorder);

        // --- 4. Battlefield (Middle Table) ---
        // We use a container to hold the Info labels + the Cards
        JPanel battlefieldContainer = new JPanel(new BorderLayout());
        battlefieldContainer.setBackground(new Color(0, 80, 0));
        battlefieldContainer.setBorder(BorderFactory.createTitledBorder(null, "TABLE", 0, 0, null, Color.WHITE));

        // Create the Status Info Panel (Top Right Corner inside Table)
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        trumpLabel.setForeground(Color.WHITE);
        deckLabel.setForeground(Color.WHITE);
        turnLabel.setForeground(Color.YELLOW);
        turnLabel.setFont(new Font("Arial", Font.BOLD, 14));

        infoPanel.add(trumpLabel);
        infoPanel.add(deckLabel);
        infoPanel.add(turnLabel);
        battlefieldContainer.add(infoPanel, BorderLayout.EAST); // Puts it in the top-right corner area

        battlefieldPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        battlefieldPanel.setOpaque(false);
        battlefieldContainer.add(battlefieldPanel, BorderLayout.CENTER);

        // --- 5. South Panel (Buttons) ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 15, 15));
        buttonPanel.setPreferredSize(new Dimension(0, 60));
        buttonPanel.add(refreshButton);
        buttonPanel.add(joinButton);

        // ASSEMBLY (Keeping your exact structure)
        JPanel mainContent = new JPanel(new BorderLayout(15, 15));
        mainContent.add(centerPanel, BorderLayout.CENTER);
        mainContent.add(handScrollPane, BorderLayout.SOUTH);
        mainContent.add(battlefieldContainer, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
        add(mainContent, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        buttonPanel.setPreferredSize(new Dimension(0, 60));
        buttonPanel.add(refreshButton);
        buttonPanel.add(joinButton);
        buttonPanel.add(doneButton); // Добавляем в GUI

        setLocationRelativeTo(null);
    }

    // --- Missing Methods required by UserAgent ---

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

    // Inside UserAgentGUI.java

    public void addDefenseToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            // Pass 'null' for agent so VisualCard turns Gray
            VisualCard serverDefCard = new VisualCard(rank, suit, null);
            battlefieldPanel.add(serverDefCard);

            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void showAttackOnTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            battlefieldPanel.removeAll(); // Clear table for the new attack
            // Server attacks are also 'null' agent -> Gray background
            VisualCard atkCard = new VisualCard(rank, suit, null);
            battlefieldPanel.add(atkCard);

            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    // --- Your Original Methods Kept Intact ---

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

    public void updateLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("> " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void addGame(String name) {
        SwingUtilities.invokeLater(() -> gameListModel.addElement(name));
    }

    public String getSelectedGame() {
        return gameList.getSelectedValue();
    }

//    public void showAttackOnTable(String rank, String suit) {
//        SwingUtilities.invokeLater(() -> {
//            battlefieldPanel.removeAll();
//            battlefieldPanel.add(new VisualCard(rank, suit, null));
//            battlefieldPanel.revalidate();
//            battlefieldPanel.repaint();
//        });
//    }

    public void clearTable() {
        SwingUtilities.invokeLater(() -> {
            battlefieldPanel.removeAll();
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    // Inside UserAgentGUI.java

    // When the SERVER beats your card (Gray card)
    public void addServerDefenseToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            VisualCard def = new VisualCard(rank, suit, null);
            def.setBackground(Color.LIGHT_GRAY); // Force Gray for Server
            battlefieldPanel.add(def);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    // When YOU beat the server's card (White card)
    public void addUserDefenseToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            // We pass 'myAgent' so it knows it's a Player card -> stays White
            VisualCard def = new VisualCard(rank, suit, myAgent);
            battlefieldPanel.add(def);
            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }

    public void prepareGameUI(String gameName) {
        SwingUtilities.invokeLater(() -> {
            boolean isDurak = gameName.toUpperCase().contains("DURAK");
            doneButton.setVisible(isDurak); // Only visible for Durak
            revalidate();
            repaint();
        });
    }

    public void addServerAttackToTable(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            // Do NOT call removeAll() here so cards accumulate
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

    public void displayEndGameMessage(String text, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            // 1. Clear everything (cards and status)
            battlefieldPanel.removeAll();

            // 2. Create the big message
            JLabel winLabel = new JLabel(text, SwingConstants.CENTER);
            winLabel.setFont(new Font("Serif", Font.BOLD, 70));
            winLabel.setForeground(textColor);

            // 3. Add a glowing/shadow effect using HTML if you like
            winLabel.setText("<html><div style='text-align: center;'>" + text + "</div></html>");

            // 4. Center it in the battlefield
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;

            battlefieldPanel.add(winLabel, gbc);

            battlefieldPanel.revalidate();
            battlefieldPanel.repaint();
        });
    }
}