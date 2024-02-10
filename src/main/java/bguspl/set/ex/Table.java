package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterfaceSwing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Iterator;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    
    /**
     * Keeping track of all tokens placed for each player 
     */
    List<Integer>[] tokensPerPlayer;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
        // TODO implement
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        //mycode
        Integer cardToRemove = slotToCard[slot];
        slotToCard[slot] = null; //TODO - see if its supposed to be null or something else
        cardToSlot[cardToRemove] = null; //TODO - see if its supposed to be null or something else

        // TODO implement
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(slotToCard[slot]!=null){
        tokensPerPlayer[player].add(slot);
            tokensPerPlayer[player].add(slot);
        // TODO implement
            env.ui.placeToken(player, slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if ( tokensPerPlayer[player].contains(slot)){
            tokensPerPlayer[player].remove(slot);
            env.ui.removeToken(player,slot);
            return true;
        }
        else {return false;}
        
    }

     /**
     * Removes a token in sfesific slot from all the players.
     * @param slot   - the slot from which to remove the token.
     * @return       - no value.
     */
    public void removeTokensFromSlot(int slot){
        for(int i=0; i<tokensPerPlayer.length; i++){
            if (tokensPerPlayer[i].contains(slot)){
                removeToken(i, slot);
            }
        }
        env.ui.removeTokens(slot);
    }
    /**
     * Checks if a player has a token in a given slot
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to check if the player has a token
     * @return       - true iff a player has a token in the given slot.
     */
    public boolean tokenExists(int player, int slot){
        for (Integer i : tokensPerPlayer[player]) {
            if(i==slot){
                return true;
            }
        }
        return false;
    }
    
    /**
     * removes all the cards from the table and add them to a list (to later return them to "deck" and shuffle deck)
     */
    public void removeAllCardsFromTable(){
        for (int i=0; i<slotToCard.length; i++){
            Integer card = slotToCard[i];
            env.ui.removeCard(i);
            slotToCard[i] = null;
            cardToSlot[card] = null;
        }
        
    }

     /**
     * inserts all the cards in table to a list
     * @return       - a list with all the Integers that represents the cards that was on the table.
     */
    public List<Integer> tableToList(){
        List<Integer> cardsOnTable = new ArrayList<>(); 
        for (int i=0; i<slotToCard.length; i++){
            Integer card = slotToCard[i];
            cardsOnTable.add(card);
        }
        return cardsOnTable;
    }
}
