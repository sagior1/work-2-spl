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
    private BlockingQueue<Player> declaredSets;
    /**
     * The thread representing the dealer.
     */
    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        declaredSets=new LinkedBlockingQueue<Player>();
        terminate = false;
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
        while (!shouldFinish()) {
            reshuffleTime=System.currentTimeMillis() + env.config.turnTimeoutMillis;//TODO not sure if this is good
            Collections.shuffle(deck);
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();// this is probably about synchronization
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        table.removeAllCardsFromTable();
        // TODO implement
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
        while(!declaredSets.isEmpty()){
            Player player = declaredSets.remove();
            if(table.tokensPerPlayer[player.getid()].size()==3){
                if(playerHasSet(player.getid())){
                    player.notifyAll();//TODO dont know if this is a good idea
                    player.score();
                    Iterator<Integer> iter = table.tokensPerPlayer[player.getid()].iterator();
                    for(int i=0;i<3&&iter.hasNext();i++){
                        int slot=iter.next();
                        table.removeCard(slot);
                        table.removeTokensFromSlot(slot);
                        updateTimerDisplay(true);
                    }
                }
                else{
                    player.notifyAll();//TODO dont know if this is a good idea
                    player.penalty();
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
    }
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        //dealerThread.sleep(1);
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            reshuffleTime=System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
            
        env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), false);

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        shuffleDeck();
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
        if(winners.size()==1){
            env.ui.announceWinner(winnersArray);
        }
    }

    /**
     * Checks if a player has a correct set
     * @param player - the player the tokens belongs to.
     * @return       - true iff a player has a correct set 
     */
    public boolean playerHasSet(int player){
        int[] cards=new int[3];
        Iterator<Integer> iter = table.tokensPerPlayer[player].iterator();
        for(int i=0;i<3&&iter.hasNext();i++){
            cards[i]=table.slotToCard[iter.next()];
        }
        return env.util.testSet(cards);
    }
    /**
     * adds a player who declared on a set
     * @param player - the player who declared.
     */
    public void addToDeclaredQueue(Player player){
        declaredSets.add(player);
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
