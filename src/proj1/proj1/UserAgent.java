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
    private boolean inGame = false;     // Для Дурака
    private boolean inBJGame = false;   // Для Блэкджека

    // MISS-CLICK PROTECTION
    private VisualCard pendingCard = null;

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

        // --- ЕДИНЫЙ СЛУШАТЕЛЬ ДЛЯ JOIN ---
        myGui.joinButton.addActionListener(e -> {
            selectedServerName = myGui.getSelectedGame();
            if (selectedServerName != null) {
                myGui.clearHand();
                myGui.clearTable();

                String serverLower = selectedServerName.toLowerCase();
                if (serverLower.contains("blackjack") || serverLower.contains("bj")) {
                    // РЕЖИМ BLACKJACK
                    inBJGame = true;
                    inGame = false;
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
                    // РЕЖИМ DURAK
                    inBJGame = false;
                    inGame = true;
                    myGui.setInGameUI(true);
                    myGui.refreshButton.setText("✋ TAKE CARDS");
                    myGui.doneButton.setText("👌 PASS / DONE");
                    myGui.joinButton.setText("🚪 JOIN SELECTED GAME");

                    myGui.trumpLabel.setVisible(true);
                    myGui.deckLabel.setVisible(true);
                    myGui.turnLabel.setVisible(true);
                    myGui.setInGameUI(true);

                    SubscribeToGame sub = new SubscribeToGame();
                    sub.setGameName1(selectedServerName);
                    sendRequest(new AID(selectedServerName, AID.ISLOCALNAME), sub);
                }
            }
        });

        // --- ЕДИНЫЙ СЛУШАТЕЛЬ ДЛЯ REFRESH (SCAN / TAKE / HIT) ---
        myGui.refreshButton.addActionListener(e -> {
            if (inBJGame) {
                sendBJCommand("HIT");
            } else if (inGame) {
                sendTakeRequest();
            } else {
                scanServers();
            }
        });

        // --- ЕДИНЫЙ СЛУШАТЕЛЬ ДЛЯ DONE (PASS / STAND) ---
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

    private void handleIncoming(ACLMessage msg) {
        if (msg.getSender().getLocalName().equalsIgnoreCase("df")) return;

        try {
            Object content = getContentManager().extractContent(msg);
            if (content instanceof Action) content = ((Action) content).getAction();

            // --- 1. HANDLE CARDS BEING DEALT/REFILLED ---
            if (content instanceof CardsDealt) {
                CardsDealt cd = (CardsDealt) content;
                jade.util.leap.Iterator it = cd.getCards().iterator();
                while (it.hasNext()) {
                    Card c = (Card) it.next();
                    if (c != null) {
                        myGui.addVisualCardToHand(c.getRank(), c.getSuit());
                    }
                }
                return;
            }

        } catch (Exception e) {
            // --- 2. HANDLE TEXT COMMANDS FROM SERVER ---
            String txt = msg.getContent();
            if (txt == null) return;

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
            else if (txt.startsWith("SHOW_DEFENSE:") || txt.startsWith("SHOW_ATTACK:")) {
                String[] p = txt.split(":");
                if (txt.startsWith("SHOW_DEFENSE:")) {
                    myGui.addUserDefenseToTable(p[1], p[2]);
                } else {
                    myGui.addUserAttackToTable(p[1], p[2]);
                }
                pendingCard = null;
            }
            else if (txt.equals("RESET_PENDING")) {
                pendingCard = null;
            }
            else if (txt.startsWith("GAME_OVER:")) {
                String result = txt.split(":")[1];
                Color c = result.contains("WIN") ? Color.ORANGE : (result.contains("DRAW") || result.contains("PUSH") ? Color.WHITE : Color.RED);
                myGui.displayEndGameMessage(result, c);
                inGame = false;
                inBJGame = false;
                myGui.refreshButton.setEnabled(true);
                myGui.refreshButton.setText("🔍 SCAN FOR LOBBIES");
                myGui.joinButton.setText("🚪 JOIN SELECTED GAME");
                myGui.doneButton.setVisible(false);
            }
//            else if (txt.startsWith("BJ_STATE:")) {
//                // Ожидаемый формат: BJ_STATE:P=A, 10:D=K, HIDDEN:SCORES:21:10
//                String[] parts = txt.split(":");
//
//                myGui.clearTable();
//                myGui.clearHand();
//
//                // --- 1. АДАПТАЦИЯ ИНТЕРФЕЙСА ---
//                // Скрываем специфику Дурака для режима Блэкджек
//                myGui.trumpLabel.setVisible(false);
//                myGui.deckLabel.setVisible(false);
//
//                // Обработка очков (Top Right Corner)
//                if (parts.length >= 6) {
//                    int pScore = Integer.parseInt(parts[4]);
//                    String dScore = parts[5];
//                    myGui.turnLabel.setText("Dealer: " + dScore + " | You: " + pScore);
//
//                    // Если перебор (Bust) — подсвечиваем счет красным
//                    if (pScore > 21) {
//                        myGui.turnLabel.setForeground(Color.RED);
//                    } else {
//                        myGui.turnLabel.setForeground(Color.CYAN);
//                    }
//                }
//
//                // --- 2. КАРТЫ ИГРОКА И ЛОГИКА SPLIT (Нижнее поле) ---
//                String playerPart = parts[1].replace("P=", "");
//                String[] pCards = playerPart.split(",");
//
//                // Split доступен только если карт 2 и они одинакового ранга
//                boolean canSplit = (pCards.length == 2 && pCards[0].trim().equals(pCards[1].trim()));
//                myGui.joinButton.setEnabled(canSplit);
//
//                for (String pRank : pCards) {
//                    String rank = pRank.trim();
//                    if (!rank.isEmpty()) {
//                        // Отрисовка в visualHandPanel (внизу)
//                        myGui.addVisualCardToHand(rank, "Hearts");
//                    }
//                }
//
//                // --- 3. КАРТЫ ДИЛЕРА (Верхнее поле / Стол) ---
//                String dealerPart = parts[2].replace("D=", "");
//                String[] dCards = dealerPart.split(",");
//                for (String dRank : dCards) {
//                    String rank = dRank.trim();
//                    if (rank.equalsIgnoreCase("HIDDEN")) {
//                        // Рисуем закрытую карту (рубашку)
//                        myGui.addServerAttackToTable("?", "BACK");
//                    } else if (!rank.isEmpty()) {
//                        // Рисуем открытую карту дилера
//                        myGui.addServerAttackToTable(rank, "Spades");
//                    }
//                }
//
//                myGui.updateLog("BJ Update: You=" + playerPart + (canSplit ? " [SPLIT AVAILABLE]" : ""));
//            }
            else if (txt.startsWith("BJ_STATE:")) {
                String[] parts = txt.split(":");
                myGui.clearTable();
                myGui.clearHand();

                // 1. UI ADAPTATION
                myGui.trumpLabel.setVisible(false);
                myGui.deckLabel.setVisible(false);
                myGui.turnLabel.setVisible(true); // Only show the turnLabel for scores

                if (parts.length >= 6) {
                    String pScoreStr = parts[4]; // Could be "21" or "BJ"
                    String dScore = parts[5];

                    myGui.turnLabel.setText("Dealer: " + dScore + " | You: " + pScoreStr);

                    if (pScoreStr.equals("BJ")) {
                        myGui.turnLabel.setForeground(Color.MAGENTA); // Special color for Blackjack
                    } else if (!pScoreStr.equals("BJ") && Integer.parseInt(pScoreStr) > 21) {
                        myGui.turnLabel.setForeground(Color.RED);
                    } else {
                        myGui.turnLabel.setForeground(Color.CYAN);
                    }
                }

                // 2. PLAYER CARDS & SPLIT
                String playerPart = parts[1].replace("P=", "");
                String[] pCards = playerPart.split(",");
                boolean canSplit = (pCards.length == 2 && pCards[0].trim().equals(pCards[1].trim()));
                myGui.joinButton.setEnabled(canSplit);

                for (String pRank : pCards) {
                    if (!pRank.trim().isEmpty()) myGui.addVisualCardToHand(pRank.trim(), "Hearts");
                }

                // 3. DEALER CARDS
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

    public void playVisualCard(VisualCard visualCardComponent) {
        if (inBJGame) return;
        if (inGame && selectedServerName != null && pendingCard == null && visualCardComponent.getParent() == myGui.visualHandPanel) {
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

                        // ОБНОВЛЕННЫЙ ФИЛЬТР: разрешаем и Дурака, и Блэкджек
                        if (nameLower.contains("durak") ||
                                nameLower.contains("blackjack") ||
                                nameLower.contains("bj")) {

                            myGui.addGame(name);
                        }
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

    private void sendBJCommand(String command) {
        if (selectedServerName != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
            msg.setContent(command);
            send(msg);
            myGui.updateLog("BJ Action: " + command);
        }
    }
}