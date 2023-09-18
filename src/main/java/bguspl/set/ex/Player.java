package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    private boolean penalty;

    /**
     * The current score of the player.
     */
    private int score;

    public LinkedList<Integer> tokens;

    private LinkedBlockingQueue<Integer> playerChoose;

    private Dealer dealer;
    public LinkedBlockingQueue <Boolean> wakeUp;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.tokens = new LinkedList<Integer>();
        this.playerChoose=new LinkedBlockingQueue<Integer>();
        wakeUp = new LinkedBlockingQueue<>();
        penalty = false;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        if (!human) aiThread.interrupt();
        while (!terminate) {
            // TODO implement main player loop
            try {
                Integer card = playerChoose.take();
                int slot = table.cardToSlot[card];
                //dealer.checkSet(cards, id);//change 
                if (tokens.contains(slot)) {
                    penalty = false;
                    table.removeToken(id, slot);
                    tokens.remove((Object) slot);
                } else if (!penalty) {
                    table.placeToken(id, slot);
                    tokens.addFirst(slot);
                }
                if (tokens.size() == 3 & !penalty){ // sent to the delear
                 //we have to delete all the slot from tokenslist after recive answer from the d.
                    dealer.pc.put(id);
                    penalty = wakeUp.take();
                    if(penalty) penalty();
                    else point();
                }
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            try {
                synchronized (this) { wait(); }
            } catch (InterruptedException ignored) {}
            while (!terminate) {
                // TODO implement player key press simulator
                int slot = (int)(Math.random()*12);
                keyPressed(slot);
                try {
                    synchronized (this) { wait(10); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try {
            if(table.slotToCard[slot] != null){
            // keysL.add(table.slotToCard[slot]);//my tokens
                playerChoose.put(table.slotToCard[slot]);// taking keys from the manger
            }
        } catch (InterruptedException e) {}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        long time = System.currentTimeMillis() + env.config.pointFreezeMillis;
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        while(time - System.currentTimeMillis() > 0)
        try {
            Thread.sleep(100);
            env.ui.setFreeze(id, time - System.currentTimeMillis());
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
            int ignored = table.countCards(); // this part is just for demonstration in
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long time = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        while(time - System.currentTimeMillis() > 0)
        try {
            Thread.sleep(100);
            env.ui.setFreeze(id, time - System.currentTimeMillis());
        } catch (InterruptedException e) {}
    }

    public int score() {
        return score;
    }
}
