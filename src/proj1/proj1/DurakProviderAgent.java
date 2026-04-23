package proj1.proj1;

import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.basic.Action;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import proj1.ontology.*;
import java.util.*;

public class DurakProviderAgent extends Agent {
    private List<Card> deck = new ArrayList<>();
    private List<Card> serverHand = new ArrayList<>();
    private int playerCardCount = 0;
    private String trumpSuit;
    private Card tableCard = null;
    private SLCodec codec = new SLCodec();

    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(CardGameOntology.getInstance());
        initDeck();
        registerInDF();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) processMessage(msg);
                else block();
            }
        });
    }

    private void processMessage(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        try {
            Object content = getContentManager().extractContent(msg);
            if (content instanceof Action) {
                jade.content.Concept action = ((Action) content).getAction();
                if (action instanceof SubscribeToGame) {
                    handleStart(msg.getSender());
                } else if (action instanceof PlayMove) {
                    Card c = ((PlayMove) action).getPlayedCard();
                    handleMove(c, msg.getSender());
                }
            } else if ("TAKE_CARDS".equals(msg.getContent())) {
                handleTake(msg.getSender());
            }
        } catch (Exception e) {
            if ("TAKE_CARDS".equals(msg.getContent())) handleTake(msg.getSender());
        }
    }

    private void handleStart(AID player) throws Exception {
        initDeck();
        serverHand.clear();
        playerCardCount = 0;
        CardsDealt cd = new CardsDealt();
        for (int i = 0; i < 6; i++) {
            Card c = drawCard();
            if (c != null) { cd.addCards(c); playerCardCount++; }
        }
        for (int i = 0; i < 6; i++) {
            Card c = drawCard();
            if (c != null) serverHand.add(c);
        }
        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
        reply.addReceiver(player);
        reply.setLanguage(codec.getName());
        reply.setOntology(CardGameOntology.getInstance().getName());
        getContentManager().fillContent(reply, cd);
        send(reply);

        sendText(player, "GAME_START:Trump is " + trumpSuit + " " + getTrumpIcon(trumpSuit));
        sendText(player, "DECK_COUNT:" + deck.size());
        makeServerMove(player);
    }

    private void handleMove(Card pCard, AID player) {
        // --- DEFENDING ---
        if (tableCard != null && canBeat(tableCard, pCard)) {
            playerCardCount--;
            sendText(player, "SHOW_DEFENSE:" + pCard.getRank() + ":" + pCard.getSuit());
            sendText(player, "✅ Correct!");

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    tableCard = null;
                    sendText(player, "CLEAR_TABLE");
                    refillHands(player);
                    sendText(player, "DECK_COUNT:" + deck.size());
                    sendText(player, "Your turn to attack!");
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
        // --- ATTACKING ---
        else if (tableCard == null) {
            tableCard = pCard;
            playerCardCount--;
            System.out.println("[ATTACK] User plays " + pCard.getRank());

            // Fix: Tell User GUI to show their attack card immediately
            sendText(player, "SHOW_ATTACK:" + pCard.getRank() + ":" + pCard.getSuit());

            new Thread(() -> {
                try {
                    Thread.sleep(1500); // Wait for user to see their card
                    handleServerDefense(player);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } else {
            sendText(player, "❌ Illegal move!");
        }
    }

    private void handleServerDefense(AID player) {
        Card bestDefense = null;
        for (Card c : serverHand) {
            if (canBeat(tableCard, c)) { bestDefense = c; break; }
        }

        if (bestDefense != null) {
            serverHand.remove(bestDefense);
            sendText(player, "SERVER_BEAT:" + bestDefense.getRank() + ":" + bestDefense.getSuit());

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    tableCard = null;
                    sendText(player, "CLEAR_TABLE");
                    refillHands(player);
                    sendText(player, "DECK_COUNT:" + deck.size());

                    // FIX: Instead of saying "Your turn", the server attacks the player!
                    sendText(player, "I defended successfully. Now I attack you!");
                    makeServerMove(player);

                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } else {
            sendText(player, "🏳️ Server takes the cards.");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    serverHand.add(tableCard);
                    tableCard = null;
                    sendText(player, "CLEAR_TABLE");
                    refillHands(player);
                    sendText(player, "Attack me again!");
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
    }

    private void refillHands(AID player) throws Exception {
        System.out.println("   [REFILL] Checking. Player has: " + playerCardCount);

        // Refill Player
        CardsDealt extra = new CardsDealt();
        boolean needToSend = false;
        while (playerCardCount < 6 && !deck.isEmpty()) {
            Card c = drawCard();
            if (c != null) {
                extra.addCards(c);
                playerCardCount++;
                needToSend = true;
            }
        }
        if (needToSend) {
            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(player);
            m.setLanguage(codec.getName());
            m.setOntology(CardGameOntology.getInstance().getName());
            getContentManager().fillContent(m, extra);
            send(m);
        }

        // Refill Server
        while (serverHand.size() < 6 && !deck.isEmpty()) {
            serverHand.add(drawCard());
        }
//        if (deck.isEmpty()) {
//            if (playerCardCount == 0 && serverHand.isEmpty()) {
//                sendText(player, "GAME_OVER:DRAW");
//                System.out.println("[GAME OVER] It's a draw!");
//            } else if (playerCardCount == 0) {
//                sendText(player, "GAME_OVER:YOU_WIN");
//                System.out.println("[GAME OVER] Player wins!");
//            } else if (serverHand.isEmpty()) {
//                sendText(player, "GAME_OVER:SERVER_WINS");
//                System.out.println("[GAME OVER] Server wins!");
//            }
//        }

        if (deck.isEmpty() && (playerCardCount == 0 || serverHand.isEmpty())) {
            // Wait a tiny bit for the last card animation to finish
            new Thread(() -> {
                try {
                    Thread.sleep(2100); // Wait for the final CLEAR_TABLE to finish
                    if (playerCardCount == 0 && serverHand.isEmpty()) {
                        sendText(player, "GAME_OVER:DRAW");
                    } else if (playerCardCount == 0) {
                        sendText(player, "GAME_OVER:YOU_WIN");
                    } else {
                        sendText(player, "GAME_OVER:SERVER_WINS");
                    }
                } catch (InterruptedException e) {}
            }).start();
        }
    }

    private void handleTake(AID player) {
        if (tableCard != null) {
            try {
                CardsDealt cd = new CardsDealt();
                cd.addCards(tableCard);
                playerCardCount++;
                tableCard = null;

                ACLMessage m = new ACLMessage(ACLMessage.INFORM);
                m.addReceiver(player);
                m.setLanguage(codec.getName());
                m.setOntology(CardGameOntology.getInstance().getName());
                getContentManager().fillContent(m, cd);
                send(m);

                sendText(player, "CLEAR_TABLE");
                sendText(player, "You took the cards. My turn!");
                makeServerMove(player);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void makeServerMove(AID player) {
        // --- DEBUG BLOCK ---
        System.out.println("\n[BOT DEBUG] My Current Hand: ");
        for (Card c : serverHand) {
            System.out.print("[" + c.getRank() + " " + getTrumpIcon(c.getSuit()) + "] ");
        }
        System.out.println("\n[BOT DEBUG] Deck Remaining: " + deck.size());
        // -------------------

        if (!serverHand.isEmpty()) {
            tableCard = serverHand.remove(0);
            System.out.println("   [BOT ACTION] Attacking with: " + tableCard.getRank() + " of " + tableCard.getSuit());
            sendAttack(player, tableCard);
        }
    }

    private boolean canBeat(Card attack, Card defense) {
        if (attack.getSuit().equals(defense.getSuit())) {
            return getWeight(defense.getRank()) > getWeight(attack.getRank());
        }
        return defense.getSuit().equals(trumpSuit);
    }

    private void sendAttack(AID player, Card c) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(player);
        m.setContent("ATTACK:" + c.getRank() + ":" + c.getSuit());
        send(m);
    }

    private void sendText(AID p, String t) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(p);
        m.setContent(t);
        send(m);
    }

    private void initDeck() {
        deck.clear();
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        for (String s : suits) {
            for (String r : ranks) {
                Card c = new Card();
                c.setSuit(s); c.setRank(r);
                deck.add(c);
            }
        }
        Collections.shuffle(deck);
        trumpSuit = suits[new Random().nextInt(4)];
    }

    private Card drawCard() { return deck.isEmpty() ? null : deck.remove(0); }

    private int getWeight(String r) {
        switch (r) {
            case "J": return 11; case "Q": return 12;
            case "K": return 13; case "A": return 14;
            case "10": return 10;
            default: try { return Integer.parseInt(r); } catch (Exception e) { return 0; }
        }
    }

    private String getTrumpIcon(String suit) {
        switch (suit) {
            case "Hearts": return "♥"; case "Diamonds": return "♦";
            case "Clubs": return "♣"; case "Spades": return "♠";
            default: return "";
        }
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("card-game-provider");
        sd.setName("DURAK-SERVER");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); }
        catch (Exception e) { e.printStackTrace(); }
    }
}