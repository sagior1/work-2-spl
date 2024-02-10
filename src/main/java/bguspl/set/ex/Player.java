package bguspl.set.ex;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
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

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The dealer of the game
     */
    private Dealer dealer;

    //manages the actions the player wants to make 
    BlockingQueue<Integer> actionsQueue;
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
        this.dealer=dealer;
        this.terminate=false;
        this.actionsQueue = new LinkedBlockingQueue<>(3);

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {

        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if (!actionsQueue.isEmpty()){
                System.out.println("enterted actions queue");
                //TODO - we need to ensure that the queue cant get more than 3 objects
                int slot = actionsQueue.remove();
                //If the token was already pressed, remove it from the table, and if not add it to the table.
                if(table.tokenExists(id, slot)){
                    table.removeToken(id, slot);
                }
                else{
                    table.placeToken(id, slot);
                    if(table.tokensPerPlayer[id].size()==3){
                        dealer.addToDeclaredQueue(this);
                        // try {
                        //     synchronized (this) { wait(); }
                        // } catch (InterruptedException ignored) {}
                        actionsQueue.clear();
                    }
                    //freeze until dealer releases
                }
            }
            // TODO implement main player loop
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
            while (!terminate) {
                int randomSlot = getRandomSlot();
                try{
                actionsQueue.put(randomSlot);}
                catch(InterruptedException ignored){}
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }
        /**
     * This method gets a random slot from the table.
     *
     * @return slot - a slot from the table.
     */
    public int getRandomSlot(){
        Random random = new Random();
        int min = 0;
        int max =table.slotToCard.length;
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try {
            actionsQueue.put(slot);
        } catch (InterruptedException e) {}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        try{
            for (long i = env.config.pointFreezeMillis; i > 0; i -= 1000) {
                env.ui.setFreeze(id, i);
                dealer.updateTimerDisplay(false);
                Thread.sleep(1000);
            }
            env.ui.setFreeze(id, 0);        }catch(InterruptedException ignored){}
        
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
         
        try{
            for(long i=env.config.penaltyFreezeMillis;i>0;i-=1000){
                env.ui.setFreeze(id, i);
                dealer.updateTimerDisplay(false);
                Thread.sleep(1000);
                
            }
            env.ui.setFreeze(id, 0);
            //TODO notifyAll();
        }catch(InterruptedException ignored){}
    }

    public int score() {
        return score;
    }
    public int getid(){
        return id;
    }


}
