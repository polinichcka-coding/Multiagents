package proj1.proj1;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// Card GUI component. Updated to support UNO +2, +4 and Black color logic.
public class VisualCard extends JPanel {
    private String rank;
    private String suit;
    private UserAgent myAgent;

    public VisualCard(String rank, String suit, UserAgent agent) {
        this.rank = rank;
        this.suit = suit;
        this.myAgent = agent;

        // Set card dimensions and layout
        setPreferredSize(new Dimension(85, 120));
        setLayout(new BorderLayout());
        setOpaque(true);

        // Apply background color based on the suit (now includes Black for +4)
        setBackground(getCardColor(suit));
        setBorder(new LineBorder(Color.WHITE, 3, true));

        // Display rank and suit symbol. Font size reduced to 22 to fit "Skip", "+2", etc.
        JLabel label = new JLabel(rank + " " + getSuitSymbol(suit), SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 22));
        label.setForeground(getTextColor(suit));

        add(label, BorderLayout.CENTER);

        // Add mouse interaction for playing cards and visual feedback
        if (myAgent != null) {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    // Send move request to the UserAgent
                    myAgent.playVisualCard(VisualCard.this);
                }

                public void mouseEntered(MouseEvent e) {
                    // Highlight the card on hover
                    setBorder(new LineBorder(Color.YELLOW, 4, true));
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                }

                public void mouseExited(MouseEvent e) {
                    // Revert border when mouse leaves
                    setBorder(new LineBorder(Color.WHITE, 3, true));
                }
            });
        }
    }

    public String getRank() { return rank; }
    public String getSuit() { return suit; }

    // Logic to determine background color for both classic and UNO decks
    private Color getCardColor(String suit) {
        if (suit == null) return Color.WHITE;

        // Standard UNO Colors
        if (suit.equalsIgnoreCase("Red")) return new Color(210, 30, 40);
        if (suit.equalsIgnoreCase("Yellow")) return new Color(245, 210, 40);
        if (suit.equalsIgnoreCase("Green")) return new Color(30, 160, 80);
        if (suit.equalsIgnoreCase("Blue")) return new Color(40, 90, 220);

        // NEW: Black color for Wild/+4 cards
        if (suit.equalsIgnoreCase("Black")) return new Color(20, 20, 20);

        // Classic Card Colors
        if (suit.equalsIgnoreCase("Hearts") || suit.equalsIgnoreCase("Diamonds"))
            return new Color(240, 240, 240);
        if (suit.equalsIgnoreCase("Clubs") || suit.equalsIgnoreCase("Spades"))
            return new Color(40, 40, 40);

        // Hidden card back
        if (suit.equalsIgnoreCase("BACK")) return Color.DARK_GRAY;

        return Color.WHITE;
    }

    // Logic to ensure text remains readable against the card background
    private Color getTextColor(String suit) {
        if (suit == null) return Color.BLACK;

        // Use black text on yellow for better contrast
        if (suit.equalsIgnoreCase("Yellow")) return Color.BLACK;

        // Red text for standard hearts/diamonds
        if (suit.equalsIgnoreCase("Hearts") || suit.equalsIgnoreCase("Diamonds"))
            return Color.RED;

        // Default to white text for dark or saturated colors (Blue, Green, Red, Black)
        return Color.WHITE;
    }

    // Returns the appropriate visual icon for the suit
    private String getSuitSymbol(String suit) {
        if (suit == null) return "";

        // Standard poker symbols
        if (suit.equalsIgnoreCase("Hearts")) return "♥";
        if (suit.equalsIgnoreCase("Diamonds")) return "♦";
        if (suit.equalsIgnoreCase("Clubs")) return "♣";
        if (suit.equalsIgnoreCase("Spades")) return "♠";

        // Dot indicator for UNO-style cards
        if (suit.equalsIgnoreCase("Red") || suit.equalsIgnoreCase("Yellow") ||
                suit.equalsIgnoreCase("Green") || suit.equalsIgnoreCase("Blue") ||
                suit.equalsIgnoreCase("Black")) {
            return "●";
        }

        return "";
    }
}