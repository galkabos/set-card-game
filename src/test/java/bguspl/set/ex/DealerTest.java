package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Vector;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DealerTest {
    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    private Player[] players = new Player[3];
    @Mock
    private Logger logger;

    @BeforeEach
    void setUp() {
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        dealer = new Dealer(env, table, players);
        for(int i = 0; i<players.length; i++)
        {
            players[i] = new Player(env,dealer,table,i,true);
        }
    }

    @Test
    void duplicateSetToRemove()
    {
        //creating the set to duplicate
        Vector<Integer> setToRemove = new Vector<>(3);
        setToRemove.add(0);
        setToRemove.add(1);
        setToRemove.add(2);

        //call the method we wand to test
        int[] ans = dealer.duplicateSetToRemove(setToRemove);

        //check if the copy was successful
        for(int i=0;i<setToRemove.size();i++)
        {
           assertEquals(ans[i], setToRemove.get(i));
        }
    }

    //test2
    @Test
    void findMaxScoreAmongPlayers()
    {
        // set the score of the players
        players[0].setScore(10);
        players[1].setScore(8);
        players[2].setScore(2);

        //call the method we wand to test
        int ans = dealer.findMaxScoreAmongPlayers();

        // check the answer
        assertEquals(ans,10);
    }


}
