package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private List<Integer> slots;

    private long timeLeft;

    public LinkedBlockingQueue<Integer> pc;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        terminate = false;
        slots =new ArrayList <Integer>();
        for (int i = 0; i <12; i++){///*env.config.rows * env.config.columns*/ 12
            slots.add(i);}
        this.pc=new LinkedBlockingQueue<Integer>();
        reshuffleTime=env.config.turnTimeoutMillis + System.currentTimeMillis();
        timeLeft = System.currentTimeMillis();
   
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(Player pl: players){
            new Thread(pl).start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && timeLeft > 0) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            placeCardsOnTable();
            Integer id = pc.poll();
            if(id != null){
                Object[] setTemp=players[id].tokens.toArray();
                if(setTemp.length == 3){
                if(table.slotToCard[(int)setTemp[0]] != null && table.slotToCard[(int)setTemp[1]] != null && table.slotToCard[(int)setTemp[2]]!= null){
                        int[] set = {table.slotToCard[(int)setTemp[0]], table.slotToCard[(int)setTemp[1]], table.slotToCard[(int)setTemp[2]]};
                        checkSet(set, id);
                    }
                }
            }
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        if (table.countCards() == 0||deck.size()==0) {
            terminate = true;
            for (int i = 0; i < players.length; i++) {
                players[i].terminate();
            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] chosenSet) {
        int slot1=table.cardToSlot[chosenSet[0]];
        int slot2=table.cardToSlot[chosenSet[1]];
        int slot3=table.cardToSlot[chosenSet[2]];
        table.removeCard(table.cardToSlot[chosenSet[0]]);
        table.placeCard(deck.remove(0), slot1);
        table.removeCard(table.cardToSlot[chosenSet[1]]);
        table.placeCard(deck.remove(0), slot2);
        table.removeCard(table.cardToSlot[chosenSet[2]]);
        table.placeCard(deck.remove(0), slot3);
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        Collections.shuffle(slots);
        for (int i = 0; i < slots.size(); i++){
            if (deck.size() > 0)
                if(table.slotToCard[slots.get(i)] == null){
                    table.placeCard(deck.remove(deck.size()-1), slots.get(i));
                }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset){
            timeLeft = env.config.turnTimeoutMillis + 999;
            reshuffleTime=env.config.turnTimeoutMillis + System.currentTimeMillis()+999;
            env.ui.setCountdown(reshuffleTime-timeLeft, reshuffleTime-timeLeft < env.config.turnTimeoutWarningMillis);
        } else {
            long sub = reshuffleTime - System.currentTimeMillis();
            timeLeft = sub;
            env.ui.setCountdown(sub, sub < env.config.turnTimeoutWarningMillis);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < 12; i++) {
            if(table.slotToCard[i] != null){
                int card = table.slotToCard[i];
                deck.add(card);
                table.removeCard(i);
                env.ui.removeTokens(i);
                env.ui.removeCard(i);
            }
        }
    }

    public void checkSet(int[] arr, int playerId) {//done
        if(arr.length==3){
            try{
                if(!env.util.testSet(arr)){
                    players[playerId].wakeUp.put(true);
                }
                else{
                    table.removeToken(playerId, table.cardToSlot[arr[0]]);
                    table.removeToken(playerId, table.cardToSlot[arr[1]]);
                    table.removeToken(playerId, table.cardToSlot[arr[2]]);
                    players[playerId].tokens.clear();
                    removeCardsFromTable(arr);
                    updateTimerDisplay(true);
                    players[playerId].wakeUp.put(false);
                }
            }catch(Exception e){  }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int score = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() > score) {
                score = players[i].score();
            }
        }
        LinkedList<Integer> temp = new LinkedList<>();
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == score) {
                temp.add(players[i].id);
            }
        }
        int[] winners = new int[temp.size()];
        
        for(int i = 0; i < temp.size(); i++){
            winners[i] = temp.get(i);
        }
        env.ui.announceWinner(winners);
    }
}
