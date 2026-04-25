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
    private List<Card> cardsOnTable = new ArrayList<>();
    private boolean playerWasDefending = false;

    protected void setup() {
        // Register language and ontology for JADE communication
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(CardGameOntology.getInstance());
        initDeck();
        registerInDF();

        System.out.println("DURAK Provider started: " + getLocalName());
        // Main agent behavior loop
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) processMessage(msg);
                else block();
            }
        });
    }

    private void processMessage(ACLMessage msg) {
        String rawContent = msg.getContent();
        if (rawContent == null) return;

        // Handle simple text-based commands
        if (rawContent.equals("TAKE_CARDS")) {
            handleTake(msg.getSender());
            return;
        }

        if (rawContent.equals("DONE_ROUND")) {
            // Defending player cannot end the round manually
            if (playerWasDefending) {
                sendText(msg.getSender(), "You are defending! Use TAKE instead.");
                return;
            }
            if (cardsOnTable.isEmpty()) {
                sendText(msg.getSender(), "Table is empty!");
                return;
            }
            handleRoundEnd(msg.getSender());
            return;
        }

        // Handle ontology-defined game actions
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
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    private void handleStart(AID player) throws Exception {
        // Initialize deck and hands for a new game
        initDeck();
        serverHand.clear();
        playerCardCount = 0;
        CardsDealt cd = new CardsDealt();

        // Initial deal to player
        for (int i = 0; i < 6; i++) {
            Card c = drawCard();
            if (c != null) {
                cd.addCards(c);
                playerCardCount++;
            }
        }
        // Initial deal to server
        for (int i = 0; i < 6; i++) {
            Card c = drawCard();
            if (c != null) serverHand.add(c);
        }

        // Send dealt cards to player agent
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
        // Player is defending against an existing card
        if (tableCard != null) {
            if (canBeat(tableCard, pCard)) {
                playerWasDefending = true;
                playerCardCount--;
                cardsOnTable.add(pCard);
                sendText(player, "SHOW_DEFENSE:" + pCard.getRank() + ":" + pCard.getSuit());
                tableCard = null;

                handleBotAddOn(player);
            } else {
                sendText(player, "RETURN_CARD:" + pCard.getRank() + ":" + pCard.getSuit());
            }
        }
        // Player is initiating an attack or adding cards
        else {
            if (!cardsOnTable.isEmpty()) {
                if (canAddOn(pCard)) {
                    processAttack(pCard, player);
                } else {
                    sendText(player, "RETURN_CARD:" + pCard.getRank() + ":" + pCard.getSuit());
                }
            } else {
                processAttack(pCard, player);
            }
        }
    }

    private void processAttack(Card pCard, AID player) {
        playerWasDefending = false;
        tableCard = pCard;
        cardsOnTable.add(pCard);
        playerCardCount--;

        sendText(player, "SHOW_ATTACK:" + pCard.getRank() + ":" + pCard.getSuit());

        addBehaviour(new WakerBehaviour(this, 1000) {
            protected void handleElapsedTimeout() {
                handleServerDefense(player);
            }
        });
    }

    private boolean canAddOn(Card pCard) {
        // Checks if the rank matches any card currently on the table
        for (Card c : cardsOnTable) {
            if (c.getRank().equals(pCard.getRank())) return true;
        }
        return false;
    }

    private void handleBotAddOn(AID player) {
        // Bot logic to throw extra cards if possible
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Card toThrow = null;
                if (playerCardCount > 0) {
                    for (Card botCard : serverHand) {
                        if (canAddOn(botCard)) {
                            toThrow = botCard;
                            break;
                        }
                    }
                }

                if (toThrow != null) {
                    serverHand.remove(toThrow);
                    tableCard = toThrow;
                    cardsOnTable.add(toThrow);
                    sendAttack(player, toThrow);
                } else {
                    Thread.sleep(1000);
                    handleRoundEnd(player);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void handleServerDefense(AID player) {
        // Bot searches for the best card to defend with
        Card bestDefense = null;
        for (Card c : serverHand) {
            if (canBeat(tableCard, c)) {
                bestDefense = c;
                break;
            }
        }

        if (bestDefense != null) {
            serverHand.remove(bestDefense);
            cardsOnTable.add(bestDefense);
            sendText(player, "SERVER_BEAT:" + bestDefense.getRank() + ":" + bestDefense.getSuit());
            tableCard = null;
        } else {
            handleBotTakes(player);
        }
    }

    private void handleBotTakes(AID player) {
        // Bot fails to defend and picks up all table cards
        sendText(player, "I can't beat that. I'm taking the cards.");
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                serverHand.addAll(cardsOnTable);
                cardsOnTable.clear();
                tableCard = null;
                sendText(player, "CLEAR_TABLE");
                refillHands(player);
                playerWasDefending = false;
                sendText(player, "SET_TURN:ATTACK");
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void handleRoundEnd(AID player) {
        // Round cleanup and turn switching
        new Thread(() -> {
            try {
                sendText(player, "CLEAR_TABLE");
                cardsOnTable.clear();
                tableCard = null;
                refillHands(player);

                if (playerWasDefending) {
                    sendText(player, "SET_TURN:ATTACK");
                    playerWasDefending = false;
                } else {
                    playerWasDefending = true;
                    makeServerMove(player);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void refillHands(AID player) throws Exception {
        // Draws cards until player and server have 6 cards or deck is empty
        if (deck.isEmpty()) {
            sendText(player, "DECK_COUNT:0");
            checkGameOver(player);
            return;
        }

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

        while (serverHand.size() < 6 && !deck.isEmpty()) {
            serverHand.add(drawCard());
        }

        sendText(player, "DECK_COUNT:" + deck.size());
        checkGameOver(player);
    }

    private void checkGameOver(AID player) {
        // Game over conditions for Durak
        if (deck.isEmpty() && (playerCardCount == 0 || serverHand.isEmpty())) {
            new Thread(() -> {
                try {
                    Thread.sleep(2100);
                    if (playerCardCount == 0 && serverHand.isEmpty()) sendText(player, "GAME_OVER:DRAW");
                    else if (playerCardCount == 0) sendText(player, "GAME_OVER:YOU_WIN");
                    else if (serverHand.isEmpty()) sendText(player, "GAME_OVER:SERVER_WINS");
                } catch (Exception e) {}
            }).start();
        }
    }

    private void handleTake(AID player) {
        // Logic for when the player picks up the cards
        if (cardsOnTable.isEmpty()) return;

        try {
            CardsDealt cd = new CardsDealt();
            for (Card c : cardsOnTable) {
                cd.addCards(c);
                playerCardCount++;
            }
            cardsOnTable.clear();
            tableCard = null;
            playerWasDefending = true;

            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(player);
            m.setLanguage(codec.getName());
            m.setOntology(CardGameOntology.getInstance().getName());
            getContentManager().fillContent(m, cd);
            send(m);

            sendText(player, "CLEAR_TABLE");

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    refillHands(player);
                    makeServerMove(player);
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void makeServerMove(AID player) {
        // Bot logic for making the first move of a round
        if (!serverHand.isEmpty()) {
            tableCard = serverHand.remove(0);
            cardsOnTable.add(tableCard);
            sendAttack(player, tableCard);
        }
    }

    private boolean canBeat(Card attack, Card defense) {
        // Core Durak mechanics for card comparison
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
        // Setup 36-card deck and random trump suit
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
        // Numerical weights for face cards and ranks
        switch (r) {
            case "J": return 11;
            case "Q": return 12;
            case "K": return 13;
            case "A": return 14;
            case "10": return 10;
            default:
                try { return Integer.parseInt(r); } catch (Exception e) { return 0; }
        }
    }

    private String getTrumpIcon(String suit) {
        // Visual representation for suits
        switch (suit) {
            case "Hearts": return "♥";
            case "Diamonds": return "♦";
            case "Clubs": return "♣";
            case "Spades": return "♠";
            default: return "";
        }
    }

    private void registerInDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("durak-game-provider");
            sd.setName("DURAK-SERVER");

            dfd.addServices(sd);
            DFService.register(this, dfd);

            System.out.println("DURAK registered in DF");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}