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
    private GamePickerGUI pickerGui;
    private UnoGameGUI unoGui;

    private SLCodec codec = new SLCodec();
    private String selectedServerName = null;

    private boolean inGame = false;       // Durak
    private boolean inBJGame = false;     // Blackjack
    private boolean inUnoGame = false;    // UNO

    private VisualCard pendingCard = null;

    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(CardGameOntology.getInstance());

        myGui = new UserAgentGUI(this);
        myGui.setVisible(false);

        pickerGui = new GamePickerGUI(this);
        pickerGui.setVisible(true);

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

        myGui.joinButton.addActionListener(e -> {
            selectedServerName = myGui.getSelectedGame();

            if (selectedServerName != null) {
                myGui.clearHand();
                myGui.clearTable();

                String serverLower = selectedServerName.toLowerCase();

                if (serverLower.contains("blackjack") || serverLower.contains("bj")) {
                    inBJGame = true;
                    inGame = false;
                    inUnoGame = false;

                    myGui.refreshButton.setText("🃏 HIT");
                    myGui.doneButton.setText("🛑 STAND");
                    myGui.joinButton.setText("✂️ SPLIT");
                    myGui.doneButton.setVisible(true);
                    myGui.updateLog("Joining Blackjack...");

                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
                    msg.setContent("JOIN_BJ");
                    send(msg);
                } else {
                    inBJGame = false;
                    inGame = true;
                    inUnoGame = false;

                    myGui.setInGameUI(true);
                    myGui.refreshButton.setText("✋ TAKE CARDS");
                    myGui.doneButton.setText("👌 PASS / DONE");
                    myGui.joinButton.setText("🚪 JOIN SELECTED GAME");

                    myGui.trumpLabel.setVisible(true);
                    myGui.deckLabel.setVisible(true);
                    myGui.turnLabel.setVisible(true);

                    SubscribeToGame sub = new SubscribeToGame();
                    sub.setGameName1(selectedServerName);
                    sendRequest(new AID(selectedServerName, AID.ISLOCALNAME), sub);
                }
            }
        });

        myGui.refreshButton.addActionListener(e -> {
            if (inBJGame) {
                sendBJCommand("HIT");
            } else if (inGame) {
                sendTakeRequest();
            } else {
                scanServers();
            }
        });

        myGui.doneButton.addActionListener(e -> {
            if (inBJGame) {
                sendBJCommand("STAND");
            } else if (inGame && selectedServerName != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
                msg.setContent("DONE_ROUND");
                send(msg);
                myGui.updateLog("👌 Sent: DONE/PASS");
            }
        });
    }

    public void openClassicLobby() {
        inUnoGame = false;
        inGame = false;
        inBJGame = false;

        pickerGui.setVisible(false);
        myGui.setVisible(true);

        myGui.updateLog("Welcome! Press SCAN to find games.");
    }

    public void openUnoGame() {
        inUnoGame = true;
        inGame = false;
        inBJGame = false;
        selectedServerName = "UNO";

        pickerGui.setVisible(false);

        unoGui = new UnoGameGUI(this);
        unoGui.setVisible(true);

        SubscribeToGame sub = new SubscribeToGame();
        sub.setGameName1("UNO");

        sendRequest(new AID("UNO", AID.ISLOCALNAME), sub);
    }

    private void handleIncoming(ACLMessage msg) {
        if (msg.getSender().getLocalName().equalsIgnoreCase("df")) return;

        try {
            Object content = getContentManager().extractContent(msg);
            if (content instanceof Action) content = ((Action) content).getAction();

            if (content instanceof CardsDealt) {
                CardsDealt cd = (CardsDealt) content;
                jade.util.leap.Iterator it = cd.getCards().iterator();

                while (it.hasNext()) {
                    Card c = (Card) it.next();

                    if (c != null) {
                        if (inUnoGame) {
                            unoGui.addCardToHand(c.getRank(), c.getSuit());
                        } else {
                            myGui.addVisualCardToHand(c.getRank(), c.getSuit());
                        }
                    }
                }
                return;
            }

        } catch (Exception e) {
            String txt = msg.getContent();
            if (txt == null) return;

            if (inUnoGame) {
                handleUnoText(txt);
                return;
            }

            handleClassicText(txt);
        }
    }

    private void handleUnoText(String txt) {
        if (txt.startsWith("UNO_TOP:")) {
            String[] p = txt.split(":");
            unoGui.showTopCard(p[1], p[2]);
            pendingCard = null; // IMPORTANT: allows next move without drawing card
        }
        else if (txt.startsWith("UNO_STATUS:")) {
            unoGui.setStatus(txt.substring("UNO_STATUS:".length()));
        }
        else if (txt.startsWith("UNO_DECK:")) {
            unoGui.setDeck(Integer.parseInt(txt.split(":")[1]));
        }
        else if (txt.startsWith("UNO_RETURN:")) {
            String[] p = txt.split(":");
            unoGui.addCardToHand(p[1], p[2]);
            pendingCard = null;
        }
        else {
            unoGui.setStatus(txt);
        }
    }

    private void handleClassicText(String txt) {
        if (txt.startsWith("DECK_COUNT:")) {
            myGui.setDeckUI(Integer.parseInt(txt.split(":")[1]));
        }
        else if (txt.startsWith("GAME_START:")) {
            String trumpPart = txt.split(":")[1].replace("Trump is ", "");
            String[] parts = trumpPart.split(" ");
            myGui.setTrumpUI(parts[0], parts.length > 1 ? parts[1] : "");
            myGui.setTurnUI(false);
            myGui.doneButton.setEnabled(false);
        }
        else if (txt.equals("CLEAR_TABLE")) {
            myGui.clearTable();
        }
        else if (txt.startsWith("SERVER_BEAT:")) {
            String[] p = txt.split(":");
            myGui.addServerDefenseToTable(p[1], p[2]);
        }
        else if (txt.startsWith("ATTACK:")) {
            String[] p = txt.split(":");
            myGui.addServerAttackToTable(p[1], p[2]);
            myGui.setTurnUI(false);
            pendingCard = null;
            myGui.doneButton.setEnabled(false);
            myGui.refreshButton.setEnabled(true);
        }
        else if (txt.startsWith("SET_TURN:")) {
            String role = txt.split(":")[1];
            boolean isAttacker = role.equals("ATTACK");
            myGui.setTurnUI(isAttacker);
            myGui.doneButton.setEnabled(isAttacker);
            myGui.refreshButton.setEnabled(!isAttacker);
        }
        else if (txt.contains("Your turn to attack") || txt.contains("Attack me again")) {
            myGui.setTurnUI(true);
            myGui.doneButton.setEnabled(true);
            myGui.refreshButton.setEnabled(false);
        }
        else if (txt.startsWith("RETURN_CARD:")) {
            String[] p = txt.split(":");
            if (pendingCard != null) {
                myGui.addVisualCardToHand(p[1], p[2]);
                pendingCard = null;
            }
        }
        else if (txt.startsWith("SHOW_DEFENSE:")) {
            String[] p = txt.split(":");
            myGui.addUserDefenseToTable(p[1], p[2]);
            pendingCard = null;
        }
        else if (txt.startsWith("SHOW_ATTACK:")) {
            String[] p = txt.split(":");
            myGui.addUserAttackToTable(p[1], p[2]);
            pendingCard = null;
        }
        else if (txt.equals("RESET_PENDING")) {
            pendingCard = null;
        }
        else if (txt.startsWith("BJ_STATE:")) {
            String[] parts = txt.split(":");
            myGui.clearTable();
            myGui.clearHand();

            myGui.trumpLabel.setVisible(false);
            myGui.deckLabel.setVisible(false);
            myGui.turnLabel.setVisible(true);

            if (parts.length >= 6) {
                String pScoreStr = parts[4];
                String dScore = parts[5];

                myGui.turnLabel.setText("Dealer: " + dScore + " | You: " + pScoreStr);

                if (pScoreStr.equals("BJ")) {
                    myGui.turnLabel.setForeground(Color.MAGENTA);
                } else if (Integer.parseInt(pScoreStr) > 21) {
                    myGui.turnLabel.setForeground(Color.RED);
                } else {
                    myGui.turnLabel.setForeground(Color.CYAN);
                }
            }

            String playerPart = parts[1].replace("P=", "");
            String[] pCards = playerPart.split(",");
            boolean canSplit = (pCards.length == 2 && pCards[0].trim().equals(pCards[1].trim()));
            myGui.joinButton.setEnabled(canSplit);

            for (String pRank : pCards) {
                if (!pRank.trim().isEmpty()) {
                    myGui.addVisualCardToHand(pRank.trim(), "Hearts");
                }
            }

            String dealerPart = parts[2].replace("D=", "");
            String[] dCards = dealerPart.split(",");

            for (String dRank : dCards) {
                String rank = dRank.trim();

                if (rank.equalsIgnoreCase("HIDDEN")) {
                    myGui.addServerAttackToTable("?", "BACK");
                } else if (!rank.isEmpty()) {
                    myGui.addServerAttackToTable(rank, "Spades");
                }
            }
        }
        else if (txt.contains("Correct!")) {
            if (pendingCard != null) {
                myGui.removeVisualCard(pendingCard);
                pendingCard = null;
            }
            myGui.updateLog("✅ Defense accepted.");
        }
        else if (txt.contains("Can't play") || txt.contains("Illegal move")) {
            myGui.updateLog("❌ " + txt);
            pendingCard = null;
        }
        else if (txt.startsWith("GAME_OVER:")) {
            String result = txt.split(":")[1];
            Color c = result.contains("WIN") ? Color.ORANGE :
                    (result.contains("DRAW") || result.contains("PUSH") ? Color.WHITE : Color.RED);

            myGui.displayEndGameMessage(result, c);

            inGame = false;
            inBJGame = false;

            myGui.refreshButton.setEnabled(true);
            myGui.refreshButton.setText("🔍 SCAN FOR LOBBIES");
            myGui.joinButton.setText("🚪 JOIN SELECTED GAME");
            myGui.doneButton.setVisible(false);
        }
        else {
            myGui.updateLog(txt);
        }


    }

    public void playVisualCard(VisualCard visualCardComponent) {
        if (inBJGame) return;

        if (inUnoGame) {
            if (pendingCard != null) return;

            pendingCard = visualCardComponent;
            unoGui.removeCard(visualCardComponent);

            PlayMove m = new PlayMove();
            Card c = new Card();

            c.setRank(visualCardComponent.getRank());
            c.setSuit(visualCardComponent.getSuit());

            m.setPlayedCard(c);

            sendRequest(new AID("UNO", AID.ISLOCALNAME), m);
            return;
        }

        if (inGame && selectedServerName != null && pendingCard == null
                && visualCardComponent.getParent() == myGui.visualHandPanel) {

            pendingCard = visualCardComponent;
            myGui.removeVisualCard(visualCardComponent);

            PlayMove m = new PlayMove();
            Card c = new Card();

            c.setRank(visualCardComponent.getRank());
            c.setSuit(visualCardComponent.getSuit());

            m.setPlayedCard(c);

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
                        String name = dfd.getName().getLocalName();
                        String nameLower = name.toLowerCase();

                        if (nameLower.contains("durak")
                                || nameLower.contains("blackjack")
                                || nameLower.contains("bj")) {
                            myGui.addGame(name);
                        }
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTakeRequest() {
        if (selectedServerName != null) {
            if (pendingCard != null) {
                myGui.addVisualCardToHand(pendingCard.getRank(), pendingCard.getSuit());
                pendingCard = null;
            }

            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
            m.setContent("TAKE_CARDS");
            send(m);

            myGui.updateLog("🏳️ Requesting to take cards...");
            myGui.refreshButton.setEnabled(false);
        }
    }

    public void sendUnoDrawRequest() {
        if (pendingCard != null) {
            unoGui.addCardToHand(pendingCard.getRank(), pendingCard.getSuit());
            pendingCard = null;
        }

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("UNO", AID.ISLOCALNAME));
        msg.setContent("UNO_DRAW");
        send(msg);

        unoGui.setStatus("Drawing card...");
    }

    private void sendBJCommand(String command) {
        if (selectedServerName != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
            msg.setContent(command);
            send(msg);

            myGui.updateLog("BJ Action: " + command);
        }
    }

    public void exitCurrentGame() {
        selectedServerName = null;
        inGame = false;
        inBJGame = false;
        inUnoGame = false;
        pendingCard = null;

        if (unoGui != null) {
            unoGui.dispose();
            unoGui = null;
        }

        if (myGui != null) {
            myGui.setVisible(false);
            myGui.clearHand();
            myGui.clearTable();
            myGui.gameListModel.clear();
        }

        if (pickerGui != null) {
            pickerGui.setVisible(true);
        }
    }
}