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
                    System.out.println("\n[JOIN] Player " + sender + " has entered the game.");
                    handleStart(msg.getSender());
                } else if (action instanceof PlayMove) {
                    Card c = ((PlayMove) action).getPlayedCard();
                    System.out.println("[MOVE] " + sender + " played: " + c.getRank() + " of " + c.getSuit());
                    handleMove(c, msg.getSender());
                }
            } else if ("TAKE_CARDS".equals(msg.getContent())) {
                System.out.println("[TAKE] " + sender + " is picking up the cards.");
                handleTake(msg.getSender());
            }
        } catch (Exception e) {
            // Fallback for raw text
            if ("TAKE_CARDS".equals(msg.getContent())) {
                System.out.println("[TAKE] " + sender + " is picking up the cards.");
                handleTake(msg.getSender());
            }
        }
    }

    private void handleStart(AID player) throws Exception {
        initDeck();
        serverHand.clear();
        playerCardCount = 0;

        CardsDealt cd = new CardsDealt();
        for (int i = 0; i < 6; i++) {
            Card c = drawCard();
            if (c != null) {
                cd.addCards(c);
                playerCardCount++;
            }
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
        makeServerMove(player);
    }

    private void handleMove(Card pCard, AID player) {
        System.out.println("   -> Game State: Player Count=" + playerCardCount + " | Table Card=" + (tableCard==null?"Empty":tableCard.getRank()));

        if (tableCard != null && canBeat(tableCard, pCard)) {
            playerCardCount--;
            System.out.println("   [SUCCESS] Defense valid. New Player Count: " + playerCardCount);

            // Tell the client to show your defense card next to the attack
            sendText(player, "SHOW_DEFENSE:" + pCard.getRank() + ":" + pCard.getSuit());
            sendText(player, "✅ Correct!");

            // Start the delay thread to clear the table and refill
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 2 second pause to see the cards
                    tableCard = null;
                    sendText(player, "CLEAR_TABLE");
                    refillHands(player);
                    sendText(player, "DECK_COUNT:" + deck.size());

                    // After YOU defend, it is now YOUR turn to attack the server
                    sendText(player, "Your turn to attack!");
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

        } else if (tableCard == null) {
            // Player is attacking
            tableCard = pCard;
            playerCardCount--; // MUST decrement here
            sendText(player, "✅ Attack accepted.");
            handleServerDefense(player);
        } else {
            // ILLEGAL MOVE - DON'T DECREMENT, DON'T REFILL
            sendText(player, "❌ Illegal move! Suit must match or be Trump.");
        }
    }

    private void handleServerDefense(AID player) {
        Card bestDefense = null;
        for (Card c : serverHand) {
            if (canBeat(tableCard, c)) {
                bestDefense = c;
                break;
            }
        }

        if (bestDefense != null) {
            serverHand.remove(bestDefense);
            // Show the server's defense card NEXT to your attack card
            sendText(player, "SERVER_BEAT:" + bestDefense.getRank() + ":" + bestDefense.getSuit());

            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 2 second pause to see the result
                    tableCard = null;
                    sendText(player, "CLEAR_TABLE");
                    refillHands(player);
                    sendText(player, "Your turn to attack again!");
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } else {
            // Server takes cards logic...
            sendText(player, "🏳️ I can't beat that! I'm taking the cards.");
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
        System.out.println("   [REFILL] Checking refill. Current Count: " + playerCardCount + " | Deck: " + deck.size());

        if (playerCardCount >= 6) {
            System.out.println("   [REFILL] Skipped: Player already has " + playerCardCount + " cards.");
            return;
        }

        CardsDealt extra = new CardsDealt();
        boolean needToSend = false;

        // Only draw from DECK if count is strictly less than 6
        while (playerCardCount < 6 && !deck.isEmpty()) {
            Card c = drawCard();
            if (c != null) {
                extra.addCards(c);
                playerCardCount++; // Increment server-side tracker
                needToSend = true;
            }
        }

        // Server refilles itself
        while (serverHand.size() < 6 && !deck.isEmpty()) {
            serverHand.add(drawCard());
            System.out.println("      > Drawing card for player. New Count: " + playerCardCount);
        }

        if (needToSend) {
            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(player);
            m.setLanguage(codec.getName());
            m.setOntology(CardGameOntology.getInstance().getName());
            getContentManager().fillContent(m, extra);
            send(m);
        }
    }

    private void handleTake(AID player) {
        try {
            System.out.println("   [SERVER] Player is taking cards from the table.");
            CardsDealt cd = new CardsDealt();

            // 1. Take ONLY the card from the table
            if (tableCard != null) {
                cd.addCards(tableCard);
                playerCardCount++; // Increment because you added a card to your hand
                tableCard = null;
            } else {
                // If they clicked 'Take' but table was empty, just ignore
                return;
            }

            // 2. Send the table cards to the player
            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(player);
            m.setLanguage(codec.getName());
            m.setOntology(CardGameOntology.getInstance().getName());
            getContentManager().fillContent(m, cd);
            send(m);

            // 3. Notify and move on
            sendText(player, "You took the cards. My turn to attack again!");

            // 4. In Durak, if you take, you don't get a refill until the NEXT round ends.
            // And the attacker (Server) attacks again.
            makeServerMove(player);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void makeServerMove(AID player) {
        System.out.println("\n[SERVER TURN]");
        System.out.println("   Deck Remaining: " + deck.size());
        System.out.print("   My Hand: ");
        for(Card c : serverHand) System.out.print("[" + c.getRank() + " " + c.getSuit() + "] ");
        System.out.println();

        if (!serverHand.isEmpty()) {
            tableCard = serverHand.remove(0);
            System.out.println("   [ATTACK] Server attacks with " + tableCard.getRank() + " of " + tableCard.getSuit());
            sendAttack(player, tableCard);
        }
    }

    private boolean canBeat(Card attack, Card defense) {
        String aSuit = attack.getSuit();
        String dSuit = defense.getSuit();
        int aWeight = getWeight(attack.getRank());
        int dWeight = getWeight(defense.getRank());

        // RULE 1: If suits are the same, higher rank wins
        if (aSuit.equals(dSuit)) {
            return dWeight > aWeight;
        }

        // RULE 2: If suits are different, defense MUST be Trump to win
        if (dSuit.equals(trumpSuit)) {
            return true; // Trump beats any non-trump
        }

        // RULE 3: Otherwise, it's an illegal move (e.g., Spades trying to beat Hearts)
        return false;
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
                c.setSuit(s);
                c.setRank(r);
                deck.add(c);
            }
        }
        Collections.shuffle(deck);
        trumpSuit = suits[new Random().nextInt(4)];
    }

    private Card drawCard() {
        return deck.isEmpty() ? null : deck.remove(0);
    }

    private int getWeight(String r) {
        switch (r) {
            case "J": return 11;
            case "Q": return 12;
            case "K": return 13;
            case "A": return 14;
            case "10": return 10;
            default:
                try { return Integer.parseInt(r); }
                catch (Exception e) { return 0; }
        }
    }

    private String getTrumpIcon(String suit) {
        switch (suit) {
            case "Hearts": return "♥";
            case "Diamonds": return "♦";
            case "Clubs": return "♣";
            case "Spades": return "♠";
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