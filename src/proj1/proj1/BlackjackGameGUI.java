package proj1.proj1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

//Manages the visual representation of the game, including cards, scoring, and controls.
public class BlackjackGameGUI extends JFrame {
    public JPanel playerHandPanel = new JPanel();
    public JPanel dealerHandPanel = new JPanel();

    public JButton hitButton = new JButton("HIT");
    public JButton standButton = new JButton("STAND");
    public JButton exitButton = new JButton("EXIT GAME");

    public JLabel scoreLabel = new JLabel("Dealer: ? | You: ?");
    public JLabel statusLabel = new JLabel("Blackjack started");

    private final UserAgent myAgent;

    public BlackjackGameGUI(UserAgent agent) {
        this.myAgent = agent;

        setTitle("Blackjack");
        setSize(1050, 730);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel main = new GradientPanel();
        main.setLayout(new BorderLayout(15, 15));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("BLACKJACK", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 42));
        title.setForeground(Color.WHITE);

        scoreLabel.setForeground(Color.CYAN);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 22));
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 15));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel top = new JPanel(new GridLayout(3, 1, 5, 5));
        top.setOpaque(false);
        top.add(title);
        top.add(scoreLabel);
        top.add(statusLabel);

        dealerHandPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        dealerHandPanel.setOpaque(false);
        dealerHandPanel.setBorder(makeBorder("DEALER"));

        playerHandPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        playerHandPanel.setOpaque(false);
        playerHandPanel.setBorder(makeBorder("YOUR HAND"));

        JPanel table = new JPanel(new GridLayout(2, 1, 10, 10));
        table.setOpaque(false);
        table.add(dealerHandPanel);
        table.add(playerHandPanel);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.setPreferredSize(new Dimension(0, 60));
        setupButton(hitButton, new Color(30, 30, 30));
        setupButton(standButton, new Color(30, 30, 30));
        setupButton(exitButton, new Color(120, 0, 0));
        buttonPanel.add(hitButton);
        buttonPanel.add(standButton);
        buttonPanel.add(exitButton);

        hitButton.addActionListener(e -> myAgent.sendBlackjackCommand("HIT"));
        standButton.addActionListener(e -> myAgent.sendBlackjackCommand("STAND"));
        exitButton.addActionListener(e -> myAgent.exitCurrentGame());

        main.add(top, BorderLayout.NORTH);
        main.add(table, BorderLayout.CENTER);
        main.add(buttonPanel, BorderLayout.SOUTH);
        add(main);

        setLocationRelativeTo(null);
    }

    private void setupButton(JButton button, Color bg) {
        button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setForeground(Color.WHITE);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
    }

    private TitledBorder makeBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                title,
                0,
                0,
                new Font("Arial", Font.BOLD, 16),
                Color.WHITE
        );
    }

    //Clears all card components from the UI
    public void clearCards() {
        SwingUtilities.invokeLater(() -> {
            playerHandPanel.removeAll();
            dealerHandPanel.removeAll();
            playerHandPanel.revalidate();
            dealerHandPanel.revalidate();
            playerHandPanel.repaint();
            dealerHandPanel.repaint();
        });
    }
    //Adds a visual card to the player's panel
    public void addPlayerCard(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            playerHandPanel.add(new VisualCard(rank, suit, null));
            playerHandPanel.revalidate();
            playerHandPanel.repaint();
        });
    }
    //Adds a visual card to the dealer's panel
    public void addDealerCard(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            dealerHandPanel.add(new VisualCard(rank, suit, null));
            dealerHandPanel.revalidate();
            dealerHandPanel.repaint();
        });
    }
    //Updates the score label with color indicators for bust/blackjack
    public void setScore(String dealer, String player) {
        SwingUtilities.invokeLater(() -> {
            scoreLabel.setText("Dealer: " + dealer + " | You: " + player);
            if ("BJ".equals(player)) {
                scoreLabel.setForeground(Color.MAGENTA);
            } else {
                try {
                    int p = Integer.parseInt(player);
                    scoreLabel.setForeground(p > 21 ? Color.RED : Color.CYAN);
                } catch (Exception e) {
                    scoreLabel.setForeground(Color.CYAN);
                }
            }
        });
    }
    //Updates the status message and logs it to console
    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        System.out.println("BLACKJACK LOG: " + text);
    }
    //Replaces the dealer panel content with a final result message
    public void showGameOver(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            dealerHandPanel.removeAll();
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 54));
            label.setForeground(color);
            dealerHandPanel.add(label);
            dealerHandPanel.revalidate();
            dealerHandPanel.repaint();
        });
    }
    //Custom JPanel with a linear gradient background and glow effects
    private static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, Color.BLACK, w, h, new Color(60, 0, 0));
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillOval(-120, -120, 300, 300);
            g2.setColor(new Color(255, 0, 0, 35));
            g2.fillOval(w - 270, h - 270, 280, 280);
        }
    }
}
