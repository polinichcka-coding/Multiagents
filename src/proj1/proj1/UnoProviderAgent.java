package proj1.proj1;

import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.basic.Action;
import proj1.ontology.*;

import java.util.*;

public class UnoProviderAgent extends Agent {

    private SLCodec codec = new SLCodec();

    private ArrayList<Card> deck = new ArrayList<>();
    private ArrayList<Card> botHand = new ArrayList<>();
    private ArrayList<Card> playerHand = new ArrayList<>();

    private Card topCard;
    private String currentActiveSuit = null;

    private boolean playerTurn = true;

    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(CardGameOntology.getInstance());

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) handleMessage(msg);
                else block();
            }
        });

        System.out.println("UNO Provider started.");
    }

    private void handleMessage(ACLMessage msg) {

        // TEXT REQUESTS
        if (msg.getContent() != null) {
            String content = msg.getContent();

            // PLAYER DRAWS
            if ("UNO_DRAW".equals(content)) {

                if (!playerTurn) {
                    sendText(msg.getSender(), "UNO_STATUS:Not your turn!");
                    return;
                }

                Card drawn = drawCard();
                if (drawn == null) {
                    sendText(msg.getSender(), "UNO_STATUS:Deck empty!");
                    return;
                }

                playerHand.add(drawn);

                CardsDealt dealt = new CardsDealt();
                dealt.addCards(drawn);
                sendCards(msg.getSender(), dealt);

                sendText(msg.getSender(), "UNO_DECK:" + deck.size());

                if (canPlay(drawn)) {
                    sendText(msg.getSender(), "UNO_STATUS:You drew a playable card. You may play it!");
                } else {
                    sendText(msg.getSender(), "UNO_STATUS:You drew a card. Dealer's turn.");
                    playerTurn = false;
                    botMove(msg.getSender());
                }
                return;
            }

            // PLAYER CHOOSES COLOR AFTER WILD
            if (content.startsWith("UNO_CHOOSE_COLOR:")) {
                String chosenColor = content.split(":")[1];

                if (!chosenColor.equalsIgnoreCase("Red") &&
                        !chosenColor.equalsIgnoreCase("Blue") &&
                        !chosenColor.equalsIgnoreCase("Green") &&
                        !chosenColor.equalsIgnoreCase("Yellow")) {
                    sendText(msg.getSender(), "UNO_STATUS:Invalid color choice!");
                    return;
                }

                currentActiveSuit = chosenColor;
                sendText(msg.getSender(), "UNO_STATUS:Color set to " + chosenColor + ". Dealer's turn.");
                playerTurn = false;
                botMove(msg.getSender());
                return;
            }
        }

        // ONTOLOGY REQUESTS
        try {
            Object content = getContentManager().extractContent(msg);

            if (content instanceof Action) {
                Object action = ((Action) content).getAction();

                if (action instanceof SubscribeToGame) {
                    startGame(msg.getSender());
                    return;
                }

                if (action instanceof PlayMove) {
                    Card played = ((PlayMove) action).getPlayedCard();
                    playCard(msg.getSender(), played);
                    return;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startGame(AID player) {

        initDeck();
        playerHand.clear();
        botHand.clear();
        playerTurn = true;

        CardsDealt cards = new CardsDealt();

        for (int i = 0; i < 7; i++) {
            Card p = drawCard();
            Card b = drawCard();

            if (p != null) {
                playerHand.add(p);
                cards.addCards(p);
            }

            if (b != null) {
                botHand.add(b);
            }
        }

        topCard = drawCard();
        while (topCard != null && topCard.getSuit().equalsIgnoreCase("Black")) {
            deck.add(topCard);
            Collections.shuffle(deck);
            topCard = drawCard();
        }

        currentActiveSuit = topCard.getSuit();

        sendCards(player, cards);

        addBehaviour(new OneShotBehaviour() {
            public void action() {
                sendText(player, "UNO_TOP:" + topCard.getRank() + ":" + topCard.getSuit());
                sendText(player, "UNO_DECK:" + deck.size());
                sendText(player, "UNO_STATUS:Game Started! Your turn.");
            }
        });
    }

    private void playCard(AID player, Card playerCard) {

        if (!playerTurn) {
            sendText(player, "UNO_STATUS:Not your turn!");
            sendText(player, "UNO_RETURN:" + playerCard.getRank() + ":" + playerCard.getSuit());
            return;
        }

        if (!handContains(playerHand, playerCard)) {
            sendText(player, "UNO_STATUS:You don't have that card!");
            sendText(player, "UNO_RETURN:" + playerCard.getRank() + ":" + playerCard.getSuit());
            return;
        }

        if (!canPlay(playerCard)) {
            sendText(player, "UNO_STATUS:Invalid move!");
            sendText(player, "UNO_RETURN:" + playerCard.getRank() + ":" + playerCard.getSuit());
            return;
        }

        removeCardFromHand(playerHand, playerCard);
        topCard = playerCard;

        if (!playerCard.getSuit().equalsIgnoreCase("Black")) {
            currentActiveSuit = playerCard.getSuit();
        }

        sendText(player, "UNO_TOP:" + topCard.getRank() + ":" + topCard.getSuit());

        if (playerHand.isEmpty()) {
            sendText(player, "UNO_GAME_OVER:YOU WIN");
            return;
        }

        // WILD
        if (playerCard.getSuit().equalsIgnoreCase("Black")) {
            sendText(player, "UNO_STATUS:Choose a color (UNO_CHOOSE_COLOR:Red/Blue/Green/Yellow)");
            return;
        }

        // SKIP / REVERSE
        if (playerCard.getRank().equalsIgnoreCase("Skip") ||
                playerCard.getRank().equalsIgnoreCase("Reverse")) {
            sendText(player, "UNO_STATUS:You skipped dealer. Play again!");
            return;
        }

        // +2
        if (playerCard.getRank().equalsIgnoreCase("+2")) {
            botDrawCards(2);
            sendText(player, "UNO_STATUS:Dealer draws 2. Your turn again!");
            return;
        }

        playerTurn = false;
        botMove(player);
    }

    // BOT MOVE WITH DELAY VISUALIZATION
    private void botMove(AID player) {

        Card chosen = choosePlayableCard(botHand);

        if (chosen == null) {
            Card drawn = drawCard();
            if (drawn != null) {
                botHand.add(drawn);
                if (canPlay(drawn)) chosen = drawn;
            }
        }

        if (chosen == null) {
            sendText(player, "UNO_STATUS:Dealer draws and passes. Your turn.");
            sendText(player, "UNO_DECK:" + deck.size());
            playerTurn = true;
            return;
        }

        botHand.remove(chosen);
        topCard = chosen;

        if (chosen.getSuit().equalsIgnoreCase("Black")) {
            currentActiveSuit = chooseBestColorForBot();
        } else {
            currentActiveSuit = chosen.getSuit();
        }

        sendText(player, "UNO_DECK:" + deck.size());

        Card finalChosen = chosen;

        // DELAY so player sees dealer card
        addBehaviour(new WakerBehaviour(this, 1500) {
            protected void onWake() {

                sendText(player, "UNO_DEALER:" + finalChosen.getRank() + ":" + finalChosen.getSuit());
                sendText(player, "UNO_TOP:" + topCard.getRank() + ":" + topCard.getSuit());
                sendText(player, "UNO_STATUS:Dealer played " + finalChosen.getRank() + " (" + currentActiveSuit + ")");

                if (botHand.isEmpty()) {
                    sendText(player, "UNO_GAME_OVER:DEALER WINS");
                    return;
                }

                // SKIP / REVERSE
                if (finalChosen.getRank().equalsIgnoreCase("Skip") ||
                        finalChosen.getRank().equalsIgnoreCase("Reverse")) {
                    sendText(player, "UNO_STATUS:Dealer skipped your turn!");
                    botMove(player);
                    return;
                }

                // +2
                if (finalChosen.getRank().equalsIgnoreCase("+2")) {
                    giveCards(player, 2);
                    sendText(player, "UNO_STATUS:Dealer played +2. You draw 2.");
                    botMove(player);
                    return;
                }

                // +4
                if (finalChosen.getRank().equalsIgnoreCase("+4")) {
                    giveCards(player, 4);
                    sendText(player, "UNO_STATUS:Dealer played +4. You draw 4.");
                    botMove(player);
                    return;
                }

                playerTurn = true;
                sendText(player, "UNO_STATUS:Your turn!");
            }
        });
    }

    private void botDrawCards(int count) {
        for (int i = 0; i < count; i++) {
            Card c = drawCard();
            if (c != null) botHand.add(c);
        }
    }

    private boolean canPlay(Card c) {
        if (c == null || topCard == null) return false;

        if (c.getSuit().equalsIgnoreCase("Black")) return true;

        return c.getSuit().equalsIgnoreCase(currentActiveSuit) ||
                c.getRank().equalsIgnoreCase(topCard.getRank());
    }

    private Card choosePlayableCard(ArrayList<Card> hand) {
        for (Card c : hand) {
            if (canPlay(c)) return c;
        }
        return null;
    }

    private String chooseBestColorForBot() {
        int red = 0, blue = 0, green = 0, yellow = 0;

        for (Card c : botHand) {
            if (c.getSuit().equalsIgnoreCase("Red")) red++;
            if (c.getSuit().equalsIgnoreCase("Blue")) blue++;
            if (c.getSuit().equalsIgnoreCase("Green")) green++;
            if (c.getSuit().equalsIgnoreCase("Yellow")) yellow++;
        }

        int max = Math.max(Math.max(red, blue), Math.max(green, yellow));

        if (max == red) return "Red";
        if (max == blue) return "Blue";
        if (max == green) return "Green";
        return "Yellow";
    }

    private boolean handContains(ArrayList<Card> hand, Card card) {
        for (Card c : hand) {
            if (c.getRank().equalsIgnoreCase(card.getRank()) &&
                    c.getSuit().equalsIgnoreCase(card.getSuit())) {
                return true;
            }
        }
        return false;
    }

    private void removeCardFromHand(ArrayList<Card> hand, Card card) {
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.getRank().equalsIgnoreCase(card.getRank()) &&
                    c.getSuit().equalsIgnoreCase(card.getSuit())) {
                hand.remove(i);
                return;
            }
        }
    }

    private void initDeck() {
        deck.clear();

        String[] colors = {"Red", "Yellow", "Green", "Blue"};
        String[] ranks = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

        for (String col : colors) {

            for (String r : ranks) {
                addCard(r, col);
                if (!r.equals("0")) addCard(r, col);
            }

            addCard("Skip", col); addCard("Skip", col);
            addCard("Reverse", col); addCard("Reverse", col);
            addCard("+2", col); addCard("+2", col);
        }

        for (int i = 0; i < 4; i++) addCard("Wild", "Black");
        for (int i = 0; i < 4; i++) addCard("+4", "Black");

        Collections.shuffle(deck);
    }

    private void addCard(String rank, String suit) {
        Card c = new Card();
        c.setRank(rank);
        c.setSuit(suit);
        deck.add(c);
    }

    private Card drawCard() {
        return deck.isEmpty() ? null : deck.remove(0);
    }

    private void giveCards(AID player, int count) {
        CardsDealt cards = new CardsDealt();

        for (int i = 0; i < count; i++) {
            Card c = drawCard();
            if (c != null) {
                cards.addCards(c);
                playerHand.add(c);
            }
        }

        sendCards(player, cards);
        sendText(player, "UNO_DECK:" + deck.size());
    }

    private void sendCards(AID player, CardsDealt cards) {
        try {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(player);
            msg.setLanguage(codec.getName());
            msg.setOntology(CardGameOntology.getInstance().getName());

            getContentManager().fillContent(msg, cards);
            send(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendText(AID player, String text) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(player);
        msg.setContent(text);
        send(msg);
    }
}