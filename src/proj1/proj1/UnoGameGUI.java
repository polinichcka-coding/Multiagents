package proj1.proj1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class UnoGameGUI extends JFrame {
    public JPanel handPanel = new JPanel();
    public JPanel tablePanel = new JPanel();

    public JButton drawButton = new JButton("DRAW CARD");
    public JButton exitButton = new JButton("EXIT GAME");

    private JLabel statusLabel = new JLabel("UNO started");
    private JLabel deckLabel = new JLabel("Deck: ?");
    private JLabel titleLabel = new JLabel("UNO");

    private final UserAgent agent;

    public UnoGameGUI(UserAgent agent) {
        this.agent = agent;

        setTitle("UNO");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel main = new GradientPanel();
        main.setLayout(new BorderLayout(15, 15));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        titleLabel.setFont(new Font("Arial", Font.BOLD, 44));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        statusLabel.setFont(new Font("Arial", Font.BOLD, 18));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        deckLabel.setFont(new Font("Arial", Font.BOLD, 16));
        deckLabel.setForeground(Color.LIGHT_GRAY);
        deckLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel top = new JPanel(new GridLayout(3, 1, 5, 5));
        top.setOpaque(false);
        top.add(titleLabel);
        top.add(statusLabel);
        top.add(deckLabel);

        tablePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 25, 25));
        tablePanel.setOpaque(false);
        tablePanel.setBorder(makeBorder("TOP CARD"));

        handPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 15));
        handPanel.setOpaque(false);

        JScrollPane handScroll = new JScrollPane(handPanel);
        handScroll.setPreferredSize(new Dimension(0, 200));
        handScroll.setOpaque(false);
        handScroll.getViewport().setOpaque(false);
        handScroll.setBorder(makeBorder("YOUR HAND"));

        setupButton(drawButton, new Color(30, 30, 30));
        setupButton(exitButton, new Color(120, 0, 0));

        drawButton.addActionListener(e -> agent.sendUnoDrawRequest());
        exitButton.addActionListener(e -> agent.exitCurrentGame());

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.setPreferredSize(new Dimension(150, 160));
        buttonPanel.add(drawButton);
        buttonPanel.add(exitButton);

        main.add(top, BorderLayout.NORTH);
        main.add(tablePanel, BorderLayout.CENTER);
        main.add(handScroll, BorderLayout.SOUTH);
        main.add(buttonPanel, BorderLayout.EAST);

        add(main);
        setLocationRelativeTo(null);
    }

    private void setupButton(JButton button, Color bg) {
        button.setFont(new Font("Arial", Font.BOLD, 16));
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

    public void addCardToHand(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            handPanel.add(new VisualCard(rank, suit, agent));
            handPanel.revalidate();
            handPanel.repaint();
        });
    }

    public void removeCard(VisualCard card) {
        SwingUtilities.invokeLater(() -> {
            handPanel.remove(card);
            handPanel.revalidate();
            handPanel.repaint();
        });
    }

    public void showTopCard(String rank, String suit) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.removeAll();
            tablePanel.add(new VisualCard(rank, suit, null));
            tablePanel.revalidate();
            tablePanel.repaint();
        });
    }

    // NEW: SHOW DEALER CARD
    public void showDealerPlayedCard(String dealerRank, String dealerSuit) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.removeAll();

            JLabel dealerLabel = new JLabel("Dealer played:");
            dealerLabel.setForeground(Color.WHITE);
            dealerLabel.setFont(new Font("Arial", Font.BOLD, 18));

            tablePanel.add(dealerLabel);
            tablePanel.add(new VisualCard(dealerRank, dealerSuit, null));

            tablePanel.revalidate();
            tablePanel.repaint();
        });
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        System.out.println("UNO LOG: " + text);
    }

    public void setDeck(int count) {
        SwingUtilities.invokeLater(() -> deckLabel.setText("Deck: " + count));
    }

    public void showGameOver(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.removeAll();
            JLabel label = new JLabel(text);
            label.setFont(new Font("Arial", Font.BOLD, 52));
            label.setForeground(color);
            tablePanel.add(label);
            tablePanel.revalidate();
            tablePanel.repaint();
        });
    }

    private static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, Color.BLACK, w, h, new Color(80, 0, 120));
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(255, 0, 0, 60));
            g2.fillOval(w - 260, 60, 230, 230);
            g2.setColor(new Color(0, 120, 255, 55));
            g2.fillOval(60, h - 250, 260, 260);
            g2.setColor(new Color(0, 255, 100, 40));
            g2.fillOval(w - 360, h - 270, 300, 300);
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillOval(-130, -130, 310, 310);
        }
    }

    public void showPlayerThenDealerCard(String playerRank, String playerSuit,
                                         String dealerRank, String dealerSuit) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.removeAll();

            JLabel playerLabel = new JLabel("You played:");
            playerLabel.setForeground(Color.WHITE);
            playerLabel.setFont(new Font("Arial", Font.BOLD, 18));

            tablePanel.add(playerLabel);
            tablePanel.add(new VisualCard(playerRank, playerSuit, null));

            tablePanel.revalidate();
            tablePanel.repaint();

            Timer timer = new Timer(2000, e -> {
                tablePanel.removeAll();

                JLabel dealerLabel = new JLabel("Dealer played:");
                dealerLabel.setForeground(Color.WHITE);
                dealerLabel.setFont(new Font("Arial", Font.BOLD, 18));

                tablePanel.add(dealerLabel);
                tablePanel.add(new VisualCard(dealerRank, dealerSuit, null));

                tablePanel.revalidate();
                tablePanel.repaint();
            });

            timer.setRepeats(false);
            timer.start();
        });
    }
}