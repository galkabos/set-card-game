package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import bguspl.set.Env;
import java.util.Random;


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
    private volatile int score;

    /**
     * keeps the answer from the dealer after he checks our set
     */
    protected int resultFromDealerAfterCheckSet;

    /**
     * keep the actions of the player
     */
    private BlockingQueue<Integer> actionQueue;

    private boolean cameBackFromPenalty = false;

    private boolean maxPresses = false;


    /**
     * The Dealer.
     */
    private Dealer dealer;

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
        this.actionQueue = new ArrayBlockingQueue<>(env.config.featureSize,true);//magic number
        this.resultFromDealerAfterCheckSet = -2;//irrelevant value
    }

    public void setCameBackFromPenalty(boolean newVal)
    {
        this.cameBackFromPenalty = newVal;
    }


    public boolean getCameBackFromPenalty()
    {
        return this.cameBackFromPenalty;
    }

    //for tests only
    public boolean getBoolTerminate()
    {
        return this.terminate;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            if(actionQueue.size() !=0)
            {
                try{
                Integer slotToTable = actionQueue.take();
                if(table.slotToCard[slotToTable] != null)
                {
                    if(table.getSetsOfTokensOfThePlayers().get(id).contains(slotToTable))
                    {
                        table.removeToken(id, slotToTable);
                        cameBackFromPenalty = false;
                    }
                    else
                    {
                        if(table.getSetsOfTokensOfThePlayers().get(id).size()<env.config.featureSize)//magic number
                            table.placeToken(id, slotToTable);
                    }
                }
                }
                catch (InterruptedException e) {}
            }
            int tmp = table.getSetsOfTokensOfThePlayers().get(id).size();
            if(tmp == env.config.featureSize && !cameBackFromPenalty)//magic number
            {
                addToDealerList(id);
            }
        }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random random = new Random();
                Integer number = random.nextInt(env.config.tableSize);//magic number
                keyPressed(number);
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if(!human)
        {
            try
            {
                aiThread.interrupt();
                aiThread.join();
            }
            catch(InterruptedException e) {}
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(Integer slot) {
        // insert the slot to queue or array limit to 3
        if(!maxPresses)
        {
            try{
            actionQueue.put(slot);
            }
            catch(InterruptedException e) {}
        }
        
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // raise one point and wait little time
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score++;
        env.ui.setScore(id, score);
        env.ui.setFreeze(id, env.config.pointFreezeMillis);//magic number
        try
        {
            long timeLeftForWait = env.config.pointFreezeMillis;//magic number
            while(timeLeftForWait>0)
            {
                env.ui.setFreeze(id, timeLeftForWait);
                long timeToWait = (env.config.pointFreezeMillis*1000)/env.config.pointFreezeMillis;//magic number
                Thread.sleep(timeToWait);
                timeLeftForWait = timeLeftForWait - timeToWait;
            }
            env.ui.setFreeze(id, 0);
        }
        catch (InterruptedException e){}
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        try
        {
            cameBackFromPenalty = true;
            long timeLeftForPenalty = env.config.penaltyFreezeMillis;
            while(timeLeftForPenalty>0)
            {
                env.ui.setFreeze(id, timeLeftForPenalty);
                long timeToWait = (env.config.pointFreezeMillis*1000)/env.config.penaltyFreezeMillis;//magic number
                Thread.sleep(timeToWait);
                timeLeftForPenalty = timeLeftForPenalty - timeToWait;
            }
            env.ui.setFreeze(id, 0);
        }
        catch (InterruptedException e){}
    }

    // for tests
    public void setScore (int score)
    {
        this.score = score;
    }

    public int score() {
        return score;
    }

    public void addToDealerList(int id)
    {
        try{
            maxPresses = true;
            dealer.requests.put(id);
            afterCheckFromDealer();
        }
        catch(InterruptedException e ){}
    }

    /**
     * Does the right action after the dealer check our set
     */
    public void afterCheckFromDealer()
    {
        if(resultFromDealerAfterCheckSet == -2)
        {
            try
            {
                //wait enough time for dealer given answer to update
                Thread.currentThread();
                Thread.sleep((env.config.turnTimeoutWarningMillis*6)/env.config.turnTimeoutWarningMillis);//magic number
            }
            catch (InterruptedException e){}
        }
        if(resultFromDealerAfterCheckSet == 0)
            point();
        else if(resultFromDealerAfterCheckSet == 1) 
                penalty();
        maxPresses = false;
        resultFromDealerAfterCheckSet = -2;
    } 
}