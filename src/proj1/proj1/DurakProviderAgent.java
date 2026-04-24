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
        String rawContent = msg.getContent();
        if (rawContent == null) return;

        if (rawContent.equals("TAKE_CARDS")) {
            System.out.println("[SERVER] TAKE_CARDS received from " + msg.getSender().getLocalName());
            handleTake(msg.getSender());
            return;
        }

        if (rawContent.equals("DONE_ROUND")) {
            // Если игрок защищается, он НЕ может нажать БИТО
            if (playerWasDefending) {
                sendText(msg.getSender(), "❌ You are defending! Use TAKE instead.");
                return;
            }
            // Если на столе пусто, нельзя нажать БИТО
            if (cardsOnTable.isEmpty()) {
                sendText(msg.getSender(), "❌ Table is empty!");
                return;
            }
            handleRoundEnd(msg.getSender());
            return;
        }

        // --- STEP 2: ONTOLOGY ACTIONS ---
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
            // Silently ignore ontology errors for non-ontology messages
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
        sendText(player, "DECK_COUNT:" + deck.size());
        makeServerMove(player);
    }

    private void handleMove(Card pCard, AID player) {
        // SCENARIO 1: PLAYER IS DEFENDING (tableCard is not null)
        if (tableCard != null) {
            if (canBeat(tableCard, pCard)) {
                playerWasDefending = true;
                playerCardCount--;
                cardsOnTable.add(pCard);
                sendText(player, "SHOW_DEFENSE:" + pCard.getRank() + ":" + pCard.getSuit());
                tableCard = null; // Card is beaten, spot is free for bot to throw more

                // Trigger bot's turn to try and add cards (Add-on)
                handleBotAddOn(player);
            } else {
                // Defense failed
                // sendText(player, "❌ Your card is too weak or wrong suit!"); // Remove or comment this
                sendText(player, "RETURN_CARD:" + pCard.getRank() + ":" + pCard.getSuit());
            }
        }
        // SCENARIO 2: PLAYER IS ATTACKING / ADDING ON (tableCard is null)
        else {
            // If there are already cards on the table, this is an ADD-ON (Throwing in)
            if (!cardsOnTable.isEmpty()) {
                if (canAddOn(pCard)) {
                    playerWasDefending = false;
                    tableCard = pCard;
                    cardsOnTable.add(pCard);
                    playerCardCount--;
                    sendText(player, "SHOW_ATTACK:" + pCard.getRank() + ":" + pCard.getSuit());

                    new Thread(() -> {
                        try { Thread.sleep(1000); handleServerDefense(player); } catch (Exception e) {}
                    }).start();
                } else {
                    // Add-on failed
                    // sendText(player, "❌ Illegal move! Rank must match cards on table."); // Remove or comment this
                    sendText(player, "RETURN_CARD:" + pCard.getRank() + ":" + pCard.getSuit());
                }
            }
            // Fresh attack (First card of the round)
            else {
                playerWasDefending = false;
                tableCard = pCard;
                cardsOnTable.add(pCard);
                playerCardCount--;
                sendText(player, "SHOW_ATTACK:" + pCard.getRank() + ":" + pCard.getSuit());

                new Thread(() -> {
                    try { Thread.sleep(1000); handleServerDefense(player); } catch (Exception e) {}
                }).start();
            }
        }
    }

    private boolean canAddOn(Card pCard) {
        for (Card c : cardsOnTable) {
            if (c.getRank().equals(pCard.getRank())) return true;
        }
        return false;
    }

    private void handleBotAddOn(AID player) {
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Card toThrow = null;
                if (playerCardCount > 0) {
                    for (Card botCard : serverHand) {
                        for (Card onTable : cardsOnTable) {
                            if (botCard.getRank().equals(onTable.getRank())) {
                                toThrow = botCard;
                                break;
                            }
                        }
                        if (toThrow != null) break;
                    }
                }

                if (toThrow != null) {
                    // Бот подкидывает карту
                    serverHand.remove(toThrow);
                    tableCard = toThrow;
                    cardsOnTable.add(toThrow);
                    sendAttack(player, toThrow);

                    System.out.println("[BOT] Adding card: " + toThrow.getRank());
                } else {
                    Thread.sleep(1000);
                    System.out.println("[BOT] No more cards to add. Round ends.");
                    handleRoundEnd(player);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
            // Бот отбился
            serverHand.remove(bestDefense);
            cardsOnTable.add(bestDefense);
            sendText(player, "SERVER_BEAT:" + bestDefense.getRank() + ":" + bestDefense.getSuit());

            tableCard = null; // Поле свободно для твоего следующего подкидывания
            sendText(player, "✅ Я отбился. Будешь подкидывать? Или жми БИТО.");
        } else {
            // Бот НЕ смог отбиться
            handleBotTakes(player);
        }
    }
    private void handleBotTakes(AID player) {
        sendText(player, "🏳️ I can't beat that. I'm taking the cards.");

        new Thread(() -> {
            try {
                Thread.sleep(1500);
                serverHand.addAll(cardsOnTable);
                cardsOnTable.clear();
                tableCard = null;

                sendText(player, "CLEAR_TABLE");
                refillHands(player);

                // КРИТИЧНО: Бот взял, значит ОН защищался и проиграл раунд.
                // Значит, ТЫ атакуешь снова.
                playerWasDefending = false;
                sendText(player, "Attack me again! Your turn.");
                // Обязательно даем игроку право ходить
                sendText(player, "SET_TURN:ATTACK");

            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void handleRoundEnd(AID player) {
        new Thread(() -> {
            try {
                sendText(player, "CLEAR_TABLE");
                cardsOnTable.clear(); // Карты уходят в "отбой", никто их не забирает
                tableCard = null;
                refillHands(player);

                if (playerWasDefending) {
                    // Если ты защищался и нажал БИТО (значит отбился) -> теперь ты атакуешь
                    sendText(player, "Your turn to attack!");
                    sendText(player, "SET_TURN:ATTACK");
                    playerWasDefending = false;
                } else {
                    // Если ты атаковал и нажал БИТО (значит закончил подкидывать) -> теперь бот атакует
                    playerWasDefending = true;
                    sendText(player, "Round finished. My turn!");
                    makeServerMove(player);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void refillHands(AID player) throws Exception {
        if (deck.isEmpty()) {
            sendText(player, "DECK_COUNT:0"); // Make sure GUI shows 0
            checkGameOver(player);
            return;
        }

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

        // --- CRITICAL FIX: Send updated count to GUI ---
        sendText(player, "DECK_COUNT:" + deck.size());

        checkGameOver(player);
    }

    // Helper to keep refillHands clean
    private void checkGameOver(AID player) {
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
        // Если список пуст — брать нечего
        if (cardsOnTable.isEmpty()) {
            sendText(player, "❌ Table is empty.");
            return;
        }

        try {
            CardsDealt cd = new CardsDealt();

            // Переносим ВСЕ карты из списка в сообщение для игрока
            for (Card c : cardsOnTable) {
                cd.addCards(c);
                playerCardCount++; // Увеличиваем счетчик в руке игрока
            }

            // ОЧИЩАЕМ ВСЁ
            cardsOnTable.clear();
            tableCard = null;
            playerWasDefending = true;

            // Отправляем игроку
            ACLMessage m = new ACLMessage(ACLMessage.INFORM);
            m.addReceiver(player);
            m.setLanguage(codec.getName());
            m.setOntology(CardGameOntology.getInstance().getName());
            getContentManager().fillContent(m, cd);
            send(m);

            sendText(player, "CLEAR_TABLE");

            // 6. Запускаем следующий ход бота с задержкой
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    refillHands(player);
                    sendText(player, "DECK_COUNT:" + deck.size());
                    makeServerMove(player); // Бот ходит под тебя снова
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

            System.out.println("[SERVER] Take successful. Player card count now: " + playerCardCount);

        } catch (Exception e) {
            System.err.println("[ERROR] handleTake failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void makeServerMove(AID player) {
        if (!serverHand.isEmpty()) {
            tableCard = serverHand.remove(0);
            cardsOnTable.add(tableCard); // ДОБАВЬ ЭТУ СТРОЧКУ, если её нет!
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
            case "J":
                return 11;
            case "Q":
                return 12;
            case "K":
                return 13;
            case "A":
                return 14;
            case "10":
                return 10;
            default:
                try {
                    return Integer.parseInt(r);
                } catch (Exception e) {
                    return 0;
                }
        }
    }

    private String getTrumpIcon(String suit) {
        switch (suit) {
            case "Hearts":
                return "♥";
            case "Diamonds":
                return "♦";
            case "Clubs":
                return "♣";
            case "Spades":
                return "♠";
            default:
                return "";
        }
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("card-game-provider");
        sd.setName("DURAK-SERVER");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}