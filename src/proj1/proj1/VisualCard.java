package proj1.proj1;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class VisualCard extends JPanel {
    private String rank;
    private String suit;
    private Color suitColor;
    private UserAgent myAgent;

    public VisualCard(String rank, String suit, UserAgent agent) {
        this.rank = rank;
        this.suit = suit;
        this.myAgent = agent;

        setPreferredSize(new Dimension(80, 120));

        // Default: White for Player, Gray for others
        if (agent != null) {
            setBackground(Color.WHITE);
        } else {
            setBackground(Color.LIGHT_GRAY);
        }

        setOpaque(true); // Ensure background color shows up
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        setLayout(null);

        // 2. Set Color based on Suit
        if (suit.equalsIgnoreCase("Hearts") || suit.equalsIgnoreCase("Diamonds")) {
            this.suitColor = Color.RED;
        } else {
            this.suitColor = Color.BLACK;
        }

        // 3. Add Rank Label (Top Left)
        JLabel rankLabel = new JLabel(rank);
        rankLabel.setFont(new Font("Arial", Font.BOLD, 18));
        rankLabel.setForeground(suitColor);
        rankLabel.setBounds(5, 5, 40, 25);
        add(rankLabel);

        // 4. Add Suit Icon (Center)
        JLabel suitLabel = new JLabel(getSuitIcon(suit));
        suitLabel.setFont(new Font("Serif", Font.PLAIN, 45)); // Serif looks better for symbols
        suitLabel.setForeground(suitColor);
        suitLabel.setHorizontalAlignment(SwingConstants.CENTER);
        suitLabel.setBounds(0, 35, 80, 50);
        add(suitLabel);

        // 5. Add Click Interaction
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (myAgent != null) {
                    // Safety check: only allow click if UserAgent is ready
                    myAgent.playVisualCard(VisualCard.this);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setBorder(new LineBorder(new Color(0, 120, 215), 3, true));
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBorder(new LineBorder(Color.BLACK, 1, true));
            }
        });
    }

    // --- Helper Methods ---

    public String getRank() { return rank; }
    public String getSuit() { return suit; }

    private String getSuitIcon(String suit) {
        switch (suit) {
            case "Hearts":   return "♥";
            case "Diamonds": return "♦";
            case "Clubs":    return "♣";
            case "Spades":   return "♠";
            default:         return suit; // Fallback to text if icon fails
        }
    }

    // For proper rendering in some LayoutManagers
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}