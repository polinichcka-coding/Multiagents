package proj1.proj1;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class VisualCard extends JPanel {
    private String rank;
    private String suit;
    private UserAgent myAgent;

    public VisualCard(String rank, String suit, UserAgent agent) {
        this.rank = rank;
        this.suit = suit;
        this.myAgent = agent;

        setPreferredSize(new Dimension(85, 120));
        setLayout(new BorderLayout());
        setOpaque(true);

        setBackground(getCardColor(suit));
        setBorder(new LineBorder(Color.WHITE, 3, true));

        JLabel label = new JLabel(rank, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 30));
        label.setForeground(getTextColor(suit));

        add(label, BorderLayout.CENTER);

        if (myAgent != null) {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    myAgent.playVisualCard(VisualCard.this);
                }

                public void mouseEntered(MouseEvent e) {
                    setBorder(new LineBorder(Color.YELLOW, 4, true));
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                }

                public void mouseExited(MouseEvent e) {
                    setBorder(new LineBorder(Color.WHITE, 3, true));
                }
            });
        }
    }

    public String getRank() {
        return rank;
    }

    public String getSuit() {
        return suit;
    }

    private Color getCardColor(String suit) {
        if (suit.equalsIgnoreCase("Red")) return new Color(210, 30, 40);
        if (suit.equalsIgnoreCase("Yellow")) return new Color(245, 210, 40);
        if (suit.equalsIgnoreCase("Green")) return new Color(30, 160, 80);
        if (suit.equalsIgnoreCase("Blue")) return new Color(40, 90, 220);

        if (suit.equalsIgnoreCase("Hearts")) return new Color(230, 60, 60);
        if (suit.equalsIgnoreCase("Diamonds")) return new Color(230, 60, 60);
        if (suit.equalsIgnoreCase("Clubs")) return new Color(40, 40, 40);
        if (suit.equalsIgnoreCase("Spades")) return new Color(40, 40, 40);

        if (suit.equalsIgnoreCase("BACK")) return Color.DARK_GRAY;

        return Color.WHITE;
    }

    private Color getTextColor(String suit) {
        if (suit.equalsIgnoreCase("Yellow")) return Color.BLACK;
        return Color.WHITE;
    }
}