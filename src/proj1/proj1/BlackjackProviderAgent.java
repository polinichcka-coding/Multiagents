package proj1.proj1;

import jade.core.*;
import jade.core.behaviours.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import proj1.ontology.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class BlackjackProviderAgent extends Agent {
    private List<Card> deck = new ArrayList<>();
    private List<Card> playerHand = new ArrayList<>();
    private List<Card> dealerHand = new ArrayList<>();
    private AID currentPlayer; // Agent ID гравця, з яким зараз йде гра
    private boolean gameOver = false;

    protected void setup() {
        String description = "Standard Blackjack Table";
        String serviceType = "card-game-provider";

        // Try to load game description from external JSON config
        try {
            byte[] encoded = Files.readAllBytes(Paths.get("blackjack_cfg.json"));
            String content = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
            if (content.contains("description")) {
                description = content.split("\"description\": \"")[1].split("\"")[0];
            }
            System.out.println("[CONFIG] Downloaded description: " + description);
        } catch (IOException e) {
            System.out.println("[CONFIG] wasn't found. Use default configuration.");
        }

        // Register the agent in the Directory Facilitator (Yellow Pages)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(description);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Service '" + serviceType + "' registered successfully.");
        } catch (Exception fe) {
            System.err.println("Error of registration in DF: " + fe.getMessage());
        }

        initDeck();
        System.out.println("♠️ Blackjack Server is ready: " + getLocalName());

        // Continuous message listening behavior
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    processMessage(msg);
                } else {
                    block(); // Suspend behavior until new message arrives
                }
            }
        });
    }

    // Deregister from DF when agent is terminated
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception e) {}
    }

    // Simple message router based on content string
    private void processMessage(ACLMessage msg) {
        String content = msg.getContent();
        if (content.equals("JOIN_BJ")) {
            startNewGame(msg.getSender());
        } else if (content.equals("HIT")) {
            handleHit(msg.getSender());
        } else if (content.equals("STAND")) {
            handleStand(msg.getSender());
        }
    }

    private void startNewGame(AID player) {
        currentPlayer = player;
        gameOver = false;
        initDeck();
        playerHand.clear();
        dealerHand.clear();

        // Initial deal: 2 cards each
        playerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        dealerHand.add(drawCard());

        // Immediate check for natural Blackjack
        if (calculateScore(playerHand) == 21) {
            updateClient();
            handleStand(player);
        } else {
            updateClient();
        }
    }

    private void handleStand(AID player) {
        if (gameOver) return;

        // Dealer logic runs in a separate thread to allow UI updates with delays
        new Thread(() -> {
            try {
                System.out.println("[BJ] Dealer reveals hidden card...");
                updateClientFull(); // Show all dealer cards
                Thread.sleep(2000);

                // Dealer must hit until score is at least 17
                while (calculateScore(dealerHand) < 17) {
                    Card newCard = drawCard();
                    dealerHand.add(newCard);
                    System.out.println("[BJ] Dealer hits: " + newCard.getRank());
                    updateClientFull();
                    Thread.sleep(2000);
                }
                determineWinner();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void determineWinner() {
        int pScore = calculateScore(playerHand);
        int dScore = calculateScore(dealerHand);
        String result;

        if (dScore > 21) {
            result = "DEALER BUST! YOU WIN!";
        } else if (pScore > dScore) {
            result = "YOU WIN!";
        } else if (dScore > pScore) {
            result = "DEALER WINS.";
        } else {
            result = "PUSH (TIE).";
        }

        endGame(result);
    }

    // Sends the full state of both hands (no hidden cards)
    private void updateClientFull() {
        String data = "BJ_STATE:P=" + handToString(playerHand) + ":D=" + handToString(dealerHand);
        sendText(currentPlayer, data);
    }

    // Blackjack scoring logic including Ace handling (1 or 11)
    private int calculateScore(List<Card> hand) {
        int score = 0;
        int aces = 0;
        for (Card c : hand) {
            String r = c.getRank();
            if (r.equals("A")) { aces++; score += 11; }
            else if (r.equals("K") || r.equals("Q") || r.equals("J") || r.equals("10")) score += 10;
            else score += Integer.parseInt(r);
        }
        // If bust, convert Aces from 11 to 1
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return score;
    }

    private void endGame(String result) {
        gameOver = true;
        sendText(currentPlayer, "GAME_OVER:" + result);
    }

    private Card drawCard() {
        if (deck.isEmpty()) initDeck();
        return deck.remove(0);
    }

    private String handToString(List<Card> hand) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            sb.append(hand.get(i).getRank());
            if (i < hand.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    // Utility method to send an ACL INFORM message
    private void sendText(AID receiver, String text) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(receiver);
        m.setContent(text);
        send(m);
    }

    // Initialize deck with 5 sets of cards (multi-deck) and shuffle
    private void initDeck() {
        deck.clear();
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};

        for (int i = 0; i < 5; i++) {
            for (String s : suits) {
                for (String r : ranks) {
                    Card c = new Card();
                    c.setRank(r);
                    c.setSuit(s);
                    deck.add(c);
                }
            }
        }
        Collections.shuffle(deck);
    }

    private void handleHit(AID player) {
        if (gameOver) return;
        playerHand.add(drawCard());
        int score = calculateScore(playerHand);

        updateClient();

        // Check for bust after hitting
        if (score > 21) {
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                endGame("BUST! YOU LOSE.");
            }).start();
        } else if (score == 21) {
            handleStand(player); // Auto-stand on 21
        }
    }

    // Updates the client while keeping the dealer's second card hidden
    private void updateClient() {
        int pScore = calculateScore(playerHand);
        boolean isBJ = (pScore == 21 && playerHand.size() == 2);
        String pLabel = isBJ ? "BJ" : String.valueOf(pScore);

        // Show only the first dealer card to the player
        int dScore = calculateScore(Collections.singletonList(dealerHand.get(0)));
        String data = "BJ_STATE:P=" + handToString(playerHand) +
                ":D=" + dealerHand.get(0).getRank() + ", HIDDEN" +
                ":SCORES:" + pLabel + ":" + dScore;
        sendText(currentPlayer, data);
    }

    // Helper to register service in JADE Yellow Pages
    private void registerInDF(String type, String desc) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(type);
        sd.setName(desc);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) { fe.printStackTrace(); }
    }
}