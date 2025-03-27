package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.Collections;
import java.util.LinkedList;

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
     * The list of card ids that are on the table;
     */
    private final List<Integer> cardsOnTheTable;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * dealer Thread
     */
    protected Thread dealerThread;

    /**
     * players Threads
     */
    protected Thread[] playersThreads;

    private boolean inFinalSeconds = false;

    /**
     * queue of the players id that request to check their set
     */
    protected BlockingQueue <Integer> requests;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playersThreads = new Thread[players.length];
        this.requests =new ArrayBlockingQueue<Integer>(players.length,true);
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.cardsOnTheTable = IntStream.range(0, env.config.rows*env.config.columns).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        reshuffle();
        
        //starting the players threads
        for(int i = 0; i<players.length; i++)
        {
            Thread t = new Thread(players[i]);
            playersThreads[i]=t;
            t.start();
        }
        while (!shouldFinish()) 
        {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;//magic number
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(false);
            sleepUntilWokenOrTimeout();
            removeAllCardsFromTableNoSet();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        for(int i=players.length-1; i>=0;i--)
        {
            players[i].terminate();
            try
            {
                playersThreads[i].interrupt();
                playersThreads[i].join();
            } 
            catch(InterruptedException e) {}
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
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTableAfterSet() {
        int k = 0;
        boolean done = false;
       
        for(int i = 0; i<table.slotToCard.length;i++)
        {
            if(table.slotToCard[i]== null)
            {
                done = false;
                while(k<deck.size() && !done)
                {
                    if(deck.get(k)!= null && !cardsOnTheTable.contains(deck.get(k)))
                    {
                        int card = deck.get(k);
                        cardsOnTheTable.add(card);
                        table.slotToCard[i] = card;
                        table.cardToSlot[card] = i;
                        env.ui.placeCard(card, i);
                        done = true;
                    }
                    k++;
                }          
            }
        }
    }

    private void placeCardsOnTable()
    {
        cardsOnTheTable.clear();
        terminate = env.util.findSets(deck, 1).isEmpty();//make sure that no sets available
        if(!terminate)
        {
            int rightSize = env.config.rows*env.config.columns;//magic number
            //check if the index is in bounds of the deck size
            if(deck.size()<rightSize)
                rightSize = deck.size();
            for(int i = 0; i<rightSize;i++)
            {
                if(deck.get(i)!= null)
                {
                    int card = deck.get(i);
                    cardsOnTheTable.add(card);
                    table.slotToCard[i] = card;
                    table.cardToSlot[card] = i;
                    env.ui.placeCard(card, i);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        Integer tmp = -1;
        try{             
            inFinalSeconds = reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis;//magic number
            if(!inFinalSeconds)
                tmp = requests.poll((env.config.turnTimeoutMillis*900)/env.config.turnTimeoutMillis, TimeUnit.MILLISECONDS);//magic number
            else { //every millisecond
                tmp = requests.poll(env.config.turnTimeoutWarningMillis/1000, TimeUnit.MILLISECONDS); //magic number
            }          
        }
        catch(InterruptedException e) {}

        if(tmp != null) 
            if(tmp.intValue()!=-1)
                checkTheSetIfRequested(tmp);
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset)
        {
            reshuffleTime=System.currentTimeMillis()+env.config.turnTimeoutMillis;//magic number
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);//magic number
        }
        else
        {
            if(reshuffleTime - System.currentTimeMillis()>env.config.turnTimeoutWarningMillis)//magic number
            {
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
            }
            else
            {
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // don't have any sets in the table, so remove all the cards on the table
        for(int i = 0;i<cardsOnTheTable.size();i++)
        {
            if(cardsOnTheTable.get(i) != null)
            {
                int card = cardsOnTheTable.remove(i);
                table.cardToSlot[card]=null;
                table.slotToCard[i] = null;
                env.ui.removeCard(i);
            }
        }
        table.removeAllTokens();
        for(int i = 0; i<players.length; i++)
        {
           table.getSetsOfTokensOfThePlayers().get(i).clear();
           players[i].setCameBackFromPenalty(false);
        }
        reshuffle();
    }

    private void removeAllCardsFromTableNoSet() {
        // don't have any sets in the table, so remove all the cards on the table
        
        if(env.util.findSets(cardsOnTheTable, 1).isEmpty())//if there is no sets on the table
        {
            removeAllCardsFromTable();
            reshuffle();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;//magic number
        }
    }

    //for tests
    public int [] duplicateSetToRemove(Vector<Integer> setToRemove)
    {
        int tmp = setToRemove.size();
        int [] tmpArray = new int [tmp];//magic number
        for(int i = 0; i<tmp; i++)
        {
            tmpArray[i] = setToRemove.get(i);
        }
        return tmpArray;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    
    private void removeCardsFromTable(Vector<Integer> setToRemove) {
        int [] tmpArray = duplicateSetToRemove(setToRemove);
        for(int i=0;i<tmpArray.length;i++)
        {
            for(int j =0; j<table.getSetsOfTokensOfThePlayers().size(); j++)
            {
                if(table.getSetsOfTokensOfThePlayers().get(j).contains(tmpArray[i]))
                {
                    table.removeToken(j, tmpArray[i]);                    
                }
            }
        
            int card = table.slotToCard[tmpArray[i]];            
            deck.remove(deck.indexOf(card));
            cardsOnTheTable.remove(cardsOnTheTable.indexOf(card));
            env.ui.removeCard(tmpArray[i]);
            table.slotToCard[tmpArray[i]] = null;
            table.cardToSlot[card] = null;

            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;//magic number
        }
    }

    //for tests
    public int findMaxScoreAmongPlayers()
    {
        int maxScore = 0;
        for(int i = 0; i<players.length; i++)
        {
            if(players[i].score()>maxScore)
                maxScore = players[i].score();
        }
        return maxScore;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = findMaxScoreAmongPlayers();
        LinkedList<Integer> theWinner = new LinkedList<>();
        for(int i = 0; i<players.length; i++)
        {
            if(players[i].score() == maxScore)
                theWinner.add(players[i].id);
        }
        int [] toUI = new int [theWinner.size()];
        for(int i = 0; i<theWinner.size(); i++)
        {
            toUI[i] = theWinner.get(i);
        }

        env.ui.announceWinner(toUI);
    }

    /**
     * Reshuffles the deck
     */
    private void reshuffle() {
        Collections.shuffle(deck);
    }

    protected void checkTheSetIfRequested(Integer playerId)
    {
        if(table.getSetsOfTokensOfThePlayers().get(playerId).size()==env.config.featureSize)//magic number
        {
            int [] setToCheck = new int [env.config.featureSize];//magic number
            int tmp = 0;
            for(int i = 0; i < setToCheck.length; i++)
            {
                Vector<Integer> v1 = table.getSetsOfTokensOfThePlayers().get(playerId);
                if(i<v1.size())//check case when the submitted vector has changed and variables has been deleted
                {
                    tmp = v1.get(i);
                    if(table.slotToCard[tmp]!= null)//check case when the submitted vector is good but the table has been changed
                        setToCheck[i] = table.slotToCard[tmp];
                    else {
                            setToCheck = null;
                            i=env.config.featureSize;//magic number
                    }    
                }
                else {
                    setToCheck = null;
                    i=env.config.featureSize;//magic number
                }
            }
            
            if(setToCheck != null)
            {
                Boolean ans = env.util.testSet(setToCheck);
                if(ans)
                {
                    // the set is ok
                    players[playerId].resultFromDealerAfterCheckSet = 0;
                    removeCardsFromTable(table.getSetsOfTokensOfThePlayers().get(playerId));
                    placeCardsOnTableAfterSet();
                }
                else
                // the set is not ok
                players[playerId].resultFromDealerAfterCheckSet = 1;
            }
        }
        else
        // the set is not in the right size
        players[playerId].resultFromDealerAfterCheckSet = 2;
    }
}