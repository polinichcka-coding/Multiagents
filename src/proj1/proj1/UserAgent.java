package proj1.proj1;

import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.basic.Action;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import proj1.ontology.*;
import javax.swing.*;
import java.awt.*;

public class UserAgent extends Agent {
    private UserAgentGUI myGui;
    private SLCodec codec = new SLCodec();
    private String selectedServerName = null;
    private boolean inGame = false;

    // MISS-CLICK PROTECTION: Holds the card until the server confirms the move is valid
    private VisualCard pendingCard = null;

    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(CardGameOntology.getInstance());

        myGui = new UserAgentGUI(this);
        myGui.setVisible(true);
        myGui.updateLog("Welcome! Press SCAN to find games.");

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleIncoming(msg);
                } else {
                    block();
                }
            }
        });

        // "SCAN" becomes "TAKE CARDS" once in a game
        myGui.refreshButton.addActionListener(e -> {
            if (!inGame) {
                scanServers();
            } else {
                sendTakeRequest();
            }
        });

        myGui.joinButton.addActionListener(e -> {
            selectedServerName = myGui.getSelectedGame();
            if (selectedServerName != null) {
                System.out.println("🚀 Joining: " + selectedServerName);
                myGui.clearHand();
                SubscribeToGame sub = new SubscribeToGame();
                sub.setGameName1(selectedServerName);
                sendRequest(new AID(selectedServerName, AID.ISLOCALNAME), sub);
                inGame = true;
                myGui.refreshButton.setText("✋ TAKE CARDS");
            }
        });
    }

    private void handleIncoming(ACLMessage msg) {
        if (msg.getSender().getLocalName().equalsIgnoreCase("df")) return;

        try {
            Object content = getContentManager().extractContent(msg);
            if (content instanceof Action) content = ((Action) content).getAction();

            // --- 1. HANDLE CARDS BEING DEALT/REFILLED ---
            if (content instanceof CardsDealt) {
                CardsDealt cd = (CardsDealt) content;
                jade.util.leap.Iterator it = cd.getCards().iterator();
                int newCards = 0;
                while (it.hasNext()) {
                    Card c = (Card) it.next();
                    if (c != null) {
                        myGui.addVisualCardToHand(c.getRank(), c.getSuit());
                        newCards++;
                    }
                }
                myGui.updateLog("📥 Received " + newCards + " cards.");
                return;
            }

        } catch (Exception e) {
            // --- 2. HANDLE TEXT COMMANDS FROM SERVER ---
            String txt = msg.getContent();
            if (txt == null) return;

            // Update Deck Count
            if (txt.startsWith("DECK_COUNT:")) {
                int count = Integer.parseInt(txt.split(":")[1]);
                myGui.setDeckUI(count);
            }
            // Start of Game / Trump Info
            else if (txt.startsWith("GAME_START:")) {
                String trumpPart = txt.split(":")[1].replace("Trump is ", "");
                String[] parts = trumpPart.split(" ");
                String suitName = parts[0];
                String icon = parts.length > 1 ? parts[1] : "";
                myGui.setTrumpUI(suitName, icon);
                myGui.setTurnUI(false); // Defending by default
            }
            // Server Attacks
            else if (txt.startsWith("ATTACK:")) {
                String[] p = txt.split(":");
                myGui.showAttackOnTable(p[1], p[2]);
                myGui.setTurnUI(false);
            }
            // Clear Table command (after the 2-second delay on server)
            else if (txt.equals("CLEAR_TABLE")) {
                myGui.clearTable();
            }
            // Turn Tracking
            else if (txt.contains("Your turn to attack") || txt.contains("Attack me again")) {
                myGui.setTurnUI(true);
                if (pendingCard != null) { // Move was successful
                    myGui.removeVisualCard(pendingCard);
                    pendingCard = null;
                }
            }
            // Move Confirmation (when you are defending)
            else if (txt.contains("Correct!")) {
                if (pendingCard != null) {
                    myGui.removeVisualCard(pendingCard);
                    pendingCard = null;
                }
                myGui.updateLog("✅ Defense accepted.");
            }
            // Handle Rejection
            else if (txt.contains("Can't play") || txt.contains("Illegal move")) {
                myGui.updateLog("❌ " + txt);
                pendingCard = null; // Move failed, keep card in hand
            }

            else if (txt.startsWith("SERVER_BEAT:")) {
                String[] parts = txt.split(":");
                // Server is defending -> Gray card
                myGui.addServerDefenseToTable(parts[1], parts[2]);
            }


            // Inside handleIncoming text processing
            else if (txt.startsWith("SHOW_ATTACK:")) {
                String[] parts = txt.split(":");
                // When YOU attack, show your card (White)
                myGui.addUserAttackToTable(parts[1], parts[2]);
                // Remove it from your hand immediately
                if (pendingCard != null) {
                    myGui.removeVisualCard(pendingCard);
                    pendingCard = null;
                }
            }
            else if (txt.startsWith("SHOW_DEFENSE:")) {
                String[] parts = txt.split(":");
                myGui.addUserDefenseToTable(parts[1], parts[2]);
            }
            // Inside handleIncoming text processing
            // Inside UserAgent.java -> handleIncoming text processing
            else if (txt.startsWith("GAME_OVER:")) {
                String result = txt.split(":")[1];

                if (result.equals("YOU_WIN")) {
                    myGui.displayEndGameMessage("You won!", Color.YELLOW);
                    myGui.updateLog("🎉 You won the game!");
                }
                else if (result.equals("SERVER_WINS")) {
                    myGui.displayEndGameMessage("You lose", Color.RED);
                    myGui.updateLog("💀 Server won. Better luck next time!");
                }
                else {
                    myGui.displayEndGameMessage("Draw", Color.WHITE);
                    myGui.updateLog("🤝 No one wins!");
                }

                inGame = false;
                myGui.refreshButton.setText("🔍 SCAN FOR LOBBIES");
            }
            else {
                myGui.updateLog(txt);
            }
        }
    }

    /**
     * Called by VisualCard when clicked
     */
    public void playVisualCard(VisualCard visualCardComponent) {
        if (inGame && selectedServerName != null) {
            pendingCard = visualCardComponent;

            PlayMove m = new PlayMove();
            Card c = new Card();
            c.setRank(visualCardComponent.getRank());
            c.setSuit(visualCardComponent.getSuit());
            m.setPlayedCard(c);

            System.out.println("[USER] Attempting to play: " + c.getRank() + " of " + c.getSuit());
            sendRequest(new AID(selectedServerName, AID.ISLOCALNAME), m);
        }
    }

    private void scanServers() {
        new Thread(() -> {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("card-game-provider");
            template.addServices(sd);
            try {
                DFAgentDescription[] results = DFService.search(this, template);
                SwingUtilities.invokeLater(() -> {
                    myGui.gameListModel.clear();
                    for (DFAgentDescription dfd : results) {
                        myGui.addGame(dfd.getName().getLocalName());
                    }
                });
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void sendRequest(AID r, jade.content.AgentAction a) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(r);
        msg.setLanguage(codec.getName());
        msg.setOntology(CardGameOntology.getInstance().getName());
        try {
            getContentManager().fillContent(msg, new Action(r, a));
            send(msg);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTakeRequest() {
        if (selectedServerName != null) {
            ACLMessage m = new ACLMessage(ACLMessage.REQUEST);
            m.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
            m.setContent("TAKE_CARDS");
            send(m);
            myGui.updateLog("🏳️ Taking table cards...");
        }
    }
}