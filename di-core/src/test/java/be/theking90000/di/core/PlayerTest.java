package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Provider;



public class PlayerTest {
    record RootScope(){};
    record Player(String name){};

    public static class PlayerScope extends Scope<Player> {
        PlayerScope(Player player){
            super(player);
        }
    };

    public static record B(Player player, RootScope rs, Scope s){}

    @Test
    void testPlayerJoin() {
        RootScope rs = new RootScope();
        Scope<RootScope> root = new Scope<>(rs);

        Player player = new Player("Joueur1");
        PlayerScope ps = new PlayerScope(player);
        ps.ownedBy(root);
        
        Player player2 = new Player("Joueur2");
        PlayerScope ps2 = new PlayerScope(player2);
        ps2.ownedBy(root);

        assertEquals(player, ps.get(B.class).player());
        assertEquals(player2, ps2.get(B.class).player());

        assertEquals(rs, ps.get(B.class).rs());
        assertEquals(rs, ps2.get(B.class).rs());

        assertEquals(ps, ps.get(B.class).s());
        assertEquals(ps2, ps2.get(B.class).s());
    }



    
}
