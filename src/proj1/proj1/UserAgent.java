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

        myGui.joinButton.addActionListener(e -> {
            selectedServerName = myGui.getSelectedGame();
            if (selectedServerName != null) {
                myGui.prepareGameUI(selectedServerName);
                System.out.println("🚀 Joining: " + selectedServerName);
                myGui.clearHand();
                SubscribeToGame sub = new SubscribeToGame();
                sub.setGameName1(selectedServerName);
                sendRequest(new AID(selectedServerName, AID.ISLOCALNAME), sub);
                inGame = true;
                myGui.refreshButton.setText("✋ TAKE CARDS");
            }
        });

        myGui.doneButton.addActionListener(e -> {
            if (inGame && selectedServerName != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
                msg.setContent("DONE_ROUND"); // Отправляем сигнал "Бито"
                send(msg);
                myGui.updateLog("👌 Отправлено: БИТО");
            }
        });
        myGui.refreshButton.addActionListener(e -> {
            System.out.println("[BUTTON CLICK] inGame: " + inGame);
            if (!inGame) {
                scanServers();
            } else {
                sendTakeRequest();
            }
        });
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
                        myGui.addVisualCardToHand(c.getRank(), c.getSuit());
                    }
                }
                return;
            }

        } catch (Exception e) {
            String txt = msg.getContent();
            if (txt == null) return;

            // --- Обработка текстовых команд ---

            if (txt.startsWith("DECK_COUNT:")) {
                myGui.setDeckUI(Integer.parseInt(txt.split(":")[1]));
            }
            else if (txt.startsWith("GAME_START:")) {
                String trumpPart = txt.split(":")[1].replace("Trump is ", "");
                String[] parts = trumpPart.split(" ");
                myGui.setTrumpUI(parts[0], parts.length > 1 ? parts[1] : "");
                myGui.setTurnUI(false);
                myGui.doneButton.setEnabled(false); // В начале игры ты защищаешься (от бота)
            }
            else if (txt.equals("CLEAR_TABLE")) {
                myGui.clearTable();
            }
            else if (txt.startsWith("SERVER_BEAT:")) {
                String[] p = txt.split(":");
                myGui.addServerDefenseToTable(p[1], p[2]);
            }
            else if (txt.startsWith("ATTACK:")) {
                // ФИКС ОШИБКИ: обязательно создаем массив p
                String[] p = txt.split(":");
                myGui.addServerAttackToTable(p[1], p[2]);

                myGui.setTurnUI(false);
                pendingCard = null; // Разблокируем UI для отбития

                // Кнопки по правилам
                myGui.doneButton.setEnabled(false);
                myGui.refreshButton.setEnabled(true); // Можно нажать TAKE
            }
            else if (txt.startsWith("SET_TURN:")) {
                String role = txt.split(":")[1];
                boolean isAttacker = role.equals("ATTACK");
                myGui.setTurnUI(isAttacker);

                // Синхронизируем кнопки
                myGui.doneButton.setEnabled(isAttacker);
                myGui.refreshButton.setEnabled(!isAttacker);
            }
            else if (txt.contains("Your turn to attack") || txt.contains("Attack me again")) {
                myGui.setTurnUI(true);
                myGui.doneButton.setEnabled(true);
                myGui.refreshButton.setEnabled(false); // Нельзя взять, когда ты атакуешь
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
                // ... твой код завершения игры ...
                String result = txt.split(":")[1];
                Color c = result.equals("YOU WIN") ? Color.ORANGE : (result.equals("DRAW") ? Color.WHITE : Color.RED);
                myGui.displayEndGameMessage(result, c);
                inGame = false;
                myGui.refreshButton.setEnabled(true);
                myGui.refreshButton.setText("🔍 SCAN FOR LOBBIES");
            }
            else {
                myGui.updateLog(txt);
            }
        }
    }

    public void playVisualCard(VisualCard visualCardComponent) {
        // Prevent clicking another card while one is waiting for server response
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
            // FORCE UNLOCK: If a card was stuck in "pending", put it back or clear it
            if (pendingCard != null) {
                myGui.addVisualCardToHand(pendingCard.getRank(), pendingCard.getSuit());
                pendingCard = null;
            }

            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(new AID(selectedServerName, AID.ISLOCALNAME));
            m.setContent("TAKE_CARDS");
            // Do NOT set ontology/language for this plain text message
            send(m);

            myGui.updateLog("🏳️ Requesting to take cards...");
            myGui.refreshButton.setEnabled(false);
        }
    }
}