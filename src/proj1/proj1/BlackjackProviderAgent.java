package proj1.proj1;

import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import proj1.ontology.*;
import java.util.*;

public class BlackjackProviderAgent extends Agent {
    private List<Card> deck = new ArrayList<>();
    private List<Card> playerHand = new ArrayList<>();
    private List<Card> dealerHand = new ArrayList<>();
    private AID currentPlayer;
    private boolean gameOver = false;

    protected void setup() {
        System.out.println("♠️ Blackjack Server Started: " + getLocalName());

        // --- РЕГИСТРАЦИЯ В DF (Чтобы UserAgent нашел лобби) ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("card-game-provider"); // Тот же тип, что и у Дурака
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception fe) {
            fe.printStackTrace();
        }

        initDeck();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    processMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    // Удаление из DF при выключении
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception e) {}
    }

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

        // Начальная раздача
        playerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard()); // Видимая
        dealerHand.add(drawCard()); // Скрытая (HIDDEN)

        if (calculateScore(playerHand) == 21) {
            updateClient();
            handleStand(player); // Сразу переходим к дилеру
        } else {
            updateClient();
        }
    }

//    private void handleHit(AID player) {
//        if (gameOver) return;
//        playerHand.add(drawCard());
//
//        if (calculateScore(playerHand) > 21) {
//            endGame("BUST! YOU LOSE.");
//        } else {
//            updateClient();
//        }
//    }

    private void handleStand(AID player) {
        if (gameOver) return;

        // Запускаем поток, чтобы не блокировать агента
        new Thread(() -> {
            try {
                // 1. Сначала "открываем" скрытую карту (просто обновляем UI без HIDDEN)
                System.out.println("[BJ] Dealer reveals hidden card...");
                updateClientFull(); // Специальный метод для показа всех карт
                Thread.sleep(2000);

                // 2. Дилер добирает карты, если нужно
                while (calculateScore(dealerHand) < 17) {
                    Card newCard = drawCard();
                    dealerHand.add(newCard);
                    System.out.println("[BJ] Dealer hits: " + newCard.getRank());

                    updateClientFull(); // Показываем новую карту игроку
                    Thread.sleep(2000); // Задержка 2 секунды перед следующим действием
                }

                // 3. Когда дилер закончил, определяем победителя
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

    // Новый метод для обновления клиента БЕЗ скрытия карт
    private void updateClientFull() {
        String data = "BJ_STATE:P=" + handToString(playerHand) + ":D=" + handToString(dealerHand);
        sendText(currentPlayer, data);
    }

    private int calculateScore(List<Card> hand) {
        int score = 0;
        int aces = 0;
        for (Card c : hand) {
            String r = c.getRank();
            if (r.equals("A")) { aces++; score += 11; }
            else if (r.equals("K") || r.equals("Q") || r.equals("J") || r.equals("10")) score += 10;
            else score += Integer.parseInt(r);
        }
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return score;
    }

//    private void updateClient() {
//        // В процессе игры (Hit) вторая карта дилера заменяется на HIDDEN
//        String data = "BJ_STATE:P=" + handToString(playerHand) + ":D=" + dealerHand.get(0).getRank() + ", HIDDEN";
//        sendText(currentPlayer, data);
//    }

    private void endGame(String result) {
        gameOver = true;
        // Отправляем финальное сообщение
        sendText(currentPlayer, "GAME_OVER:" + result);
    }

//    private void initDeck() {
//        deck.clear();
//        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
//        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
//        for (String s : suits) {
//            for (String r : ranks) {
//                Card c = new Card();
//                c.setRank(r);
//                c.setSuit(s);
//                deck.add(c);
//            }
//        }
//        Collections.shuffle(deck);
//    }

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

    private void sendText(AID receiver, String text) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.addReceiver(receiver);
        m.setContent(text);
        send(m);
    }

    // Измени инициализацию колоды на 5 штук
    private void initDeck() {
        deck.clear();
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};

        for (int i = 0; i < 5; i++) { // ПЯТЬ КОЛОД
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

        if (score > 21) {
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                endGame("BUST! YOU LOSE.");
            }).start();
        } else if (score == 21) {
            // Standard 21 (from hitting), not a Natural BJ
            handleStand(player);
        }
    }

    private void updateClient() {
        int pScore = calculateScore(playerHand);
        // Check for Natural Blackjack (only possible with exactly 2 cards)
        boolean isBJ = (pScore == 21 && playerHand.size() == 2);

        String pLabel = isBJ ? "BJ" : String.valueOf(pScore);
        int dScore = calculateScore(Collections.singletonList(dealerHand.get(0)));

        // We send "BJ" as the score if it's a natural
        String data = "BJ_STATE:P=" + handToString(playerHand) +
                ":D=" + dealerHand.get(0).getRank() + ", HIDDEN" +
                ":SCORES:" + pLabel + ":" + dScore;
        sendText(currentPlayer, data);
    }
}