package proj1.proj1;

import javax.swing.*;
import java.awt.*;

public class GamePickerGUI extends JFrame {
    public GamePickerGUI(UserAgent agent) {
        // Window setup
        setTitle("Choose Game");
        setSize(420, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Container for game selection buttons
        JPanel main = new JPanel(new GridLayout(3, 1, 15, 15));
        main.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        main.setBackground(Color.BLACK);

        // Create game buttons
        JButton blackjack = makeButton("BLACKJACK");
        JButton durak = makeButton("DURAK");
        JButton uno = makeButton("UNO");

        // Set action listeners to trigger specific games via the UserAgent
        blackjack.addActionListener(e -> agent.startBlackjackGame());
        durak.addActionListener(e -> agent.startDurakGame());
        uno.addActionListener(e -> agent.startUnoGame());

        // Assembly
        main.add(blackjack);
        main.add(durak);
        main.add(uno);
        add(main, BorderLayout.CENTER);

        // Center window on screen
        setLocationRelativeTo(null);
    }

    // Helper method to create styled UI buttons
    private JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Arial", Font.BOLD, 24));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(30, 30, 30));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        return b;
    }
}