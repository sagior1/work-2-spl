package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;
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

    /**
     * a queue of players who declared that they have a set
     */
    
    protected volatile boolean freezePlayers;

    /**
     * The thread representing the dealer.
     */
    private Thread dealerThread;

    /**
     * for managing the amout of time the dealer needs to wait
     */
    private long sleepingManager= Long.MAX_VALUE;
    /**
     * finals for updating the timer correctly
     */
    public static final int ONESECOND = 1000;
    public static final int TENMILIS = 10;
    public static final int ONEMILIS = 1;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        //  declaredSets=new LinkedBlockingQueue<Player>();
        terminate = false;
        freezePlayers=true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player, player.id + " ");
            playerThread.start();
        }
        Collections.shuffle(deck);
        while (!shouldFinish()) {
            placeCardsOnTable();
            table.hints();
            //reshuffleTime=System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(true);
            timerLoop();
            //updateTimerDisplay(false);
            removeAllCardsFromTable();
            
        }
        announceWinners();
        if(!terminate){
            terminate();
        }
        try{
            Thread.sleep(env.config.endGamePauseMillies);
       }catch(InterruptedException e){}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && (System.currentTimeMillis() < reshuffleTime || (env.config.turnTimeoutMillis <= 0))) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();

            //table.hints();
            //env.logger.info("player")
            if(env.config.turnTimeoutMillis <= 0){
                List<Integer> cardsFromTable = table.tableToList();
                if (env.util.findSets(cardsFromTable, 1).size() == 0) {
                    break;
                }
            }
        }

    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        freezePlayers=true;
        //table.removeAllCardsFromTable();
        env.logger.info("dealer starting terminate.");
        try{
            for (int i=players.length-1; i >= 0; i--){
                players[i].terminate();
                players[i].getThread().interrupt();
                players[i].getThread().join();
            }
            terminate = true;
            Thread.currentThread().interrupt();
        }catch(InterruptedException ignored) {}
         
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
    private void removeCardsFromTable() {//need to make sure if there are sets found here
        synchronized (table.setsDeclared) {
            
        while(!table.setsDeclared.isEmpty()){
            int playerid=table.setsDeclared.remove();
            int idforcheck=playerid+1;
            env.logger.info("checking set of player " + idforcheck);

            if(table.tokensPerPlayer[playerid].size()==env.config.featureSize){
                Player player=players[playerid];                
                if(table.tokensPerPlayer[playerid].size()<env.config.featureSize){
                    player.decisionQueue.add(0);
                    env.logger.info("releasing player because set is too short for player " + idforcheck);
                }
                else{
                if(playerHasSet(playerid)){
                    player.decisionQueue.add(1);                   
                    for(Integer i=0;i<env.config.featureSize;i++){
                        int slot=table.tokensPerPlayer[playerid].get(0);
                        table.removeCard(slot);
                        table.removeTokensFromSlot(slot);
                    }
                    env.logger.info("giving point to player " + idforcheck);
                    table.setsDeclared.notifyAll();
                    updateTimerDisplay(true);
                    

                }
                else{
                    env.logger.info("giving penalty to player " + idforcheck);
                    player.decisionQueue.add(-1);
                    table.setsDeclared.notifyAll();
                }
            }

            }
            synchronized(players[playerid].decisionQueue){
                env.logger.info("dealer succefuly synchronied on player decision queue for player " + idforcheck);
                
                players[playerid].decisionQueue.notifyAll();
            }
            }
            
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for(int i=0;i<table.slotToCard.length;i++){
            if(!deck.isEmpty()&&table.slotToCard[i]==null){
                int card=deck.remove(deck.size()-1);
                table.placeCard(card, i);
            }
        }
        freezePlayers=false;
    }
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        
        long waitLength=sleepingManager - System.currentTimeMillis();
        env.logger.info("wait length is: "+ waitLength);
        synchronized(table.setsDeclared){
            //if()
            if(table.setsDeclared.isEmpty()&&waitLength>ONEMILIS){
                try {
                    env.logger.info("dealer going to sleep");
                    table.setsDeclared.wait(waitLength);
                    env.logger.info("dealer waking up");
                } catch(InterruptedException ignored){}
            }
        }
    }
    

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long timeToDisplay=0;
        if(env.config.turnTimeoutMillis>0){
            if(reset){
                reshuffleTime=System.currentTimeMillis() + env.config.turnTimeoutMillis;
                sleepingManager= reshuffleTime - env.config.turnTimeoutMillis;;
                env.ui.setCountdown(env.config.turnTimeoutMillis-TENMILIS, false);
            }
            else{ 
                if (System.currentTimeMillis() >= sleepingManager) {
                    timeToDisplay=reshuffleTime-sleepingManager;
                    env.logger.info("time to display is: "+timeToDisplay);
                    if(timeToDisplay<=env.config.turnTimeoutWarningMillis){
                        timeToDisplay=reshuffleTime-System.currentTimeMillis();
                        if(timeToDisplay<=0){
                            env.ui.setCountdown(0, true);
                        }
                        else
                            env.ui.setCountdown(timeToDisplay, true);
                        sleepingManager+=TENMILIS;
                    }
                    else{
                        env.ui.setCountdown(reshuffleTime-sleepingManager, false);
                        sleepingManager+=ONESECOND;
                    }
                }
            }
        }
        if(env.config.turnTimeoutMillis==0){
            if(reset){
                reshuffleTime=System.currentTimeMillis();
                sleepingManager=reshuffleTime;
                timeToDisplay=0;
            }
            else{
                timeToDisplay=sleepingManager-reshuffleTime;
            }
            if(sleepingManager<=System.currentTimeMillis()){
                env.ui.setElapsed(timeToDisplay);
                sleepingManager += ONESECOND;
            }

        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        if(env.config.turnTimeoutMillis>0){
            freezePlayers=true;
            shuffleDeck();
            for (Player player : players) {
                player.actionsQueue.clear();
            }
        }
        if(env.config.turnTimeoutMillis<=0){
            List<Integer> cardsFromTable = table.tableToList();
            env.logger.info("has set in table? "+env.util.findSets(cardsFromTable, 1).size());
            if(env.util.findSets(cardsFromTable, 1).size() == 0){
                freezePlayers=true;
                shuffleDeck();
                for (Player player : players) {
                    player.actionsQueue.clear();
                }
                freezePlayers=false;
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        Integer maxScore=Integer.MIN_VALUE;
        List<Player> winners = new LinkedList<>();
        for (Player player : players) {
            if(player.score()>maxScore){
                maxScore=player.score();
            }
        }
        for (Player player : players) {
            if(player.score()==maxScore){
                winners.add(player);
            }
        }
        int[] winnersArray=new int[winners.size()];
        int i=0;
        for (Player player : winners) {
            winnersArray[i]=player.getid();
            i++;
        }
            env.ui.announceWinner(winnersArray);
    }

    /**
     * Checks if a player has a correct set
     * @param player - the player the tokens belongs to.
     * @return       - true iff a player has a correct set 
     */
    public boolean playerHasSet(int player){
        if(table.tokensPerPlayer[player].size()<env.config.featureSize){
            return false;
        }
        int[] cards=new int[env.config.featureSize];
        Iterator<Integer> iter = table.tokensPerPlayer[player].iterator();
        for(int i=0;i<env.config.featureSize&&iter.hasNext();i++){
            cards[i]=table.slotToCard[iter.next()];
        }
        return env.util.testSet(cards);
    }


    /**
     * adds all the cards that we removed from the tablr to deck and shuffle deck.
     * @return       - true iff a player has a correct set 
     */
    public void shuffleDeck(){
        List<Integer> cardsFromTable = table.tableToList();
        deck.addAll(cardsFromTable);
        Collections.shuffle(deck);
        table.removeAllCardsFromTable();
    }



}
