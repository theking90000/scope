package be.theking90000.di.core;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;



public class PlayerTest {
    record RootScope(){};
    record Player(String name){};

    public static class PlayerScope extends Scope<Player> {
        PlayerScope(Player player){
            super(player);
        }
    };

    public static record B(Player player, RootScope rs, Scope s){}

    public static class GamePlayer { int stat = 0;
        Player player;
        Game game;
         public GamePlayer(Player player, Game game){
            this.player=player;
            this.game = game;
        }

        void print() {
            System.out.println("Je suis "+player.name+" Dans la partie "+game.name + ", j'ai "+stat+" stat");
        }
    
    };

    record GamePlayerScope(Player player, Game game){};
    
    record GameScope(String name){};

    public static class PlayerList{
        ArrayList<GamePlayer> players;
        public PlayerList(){
            this.players = new ArrayList<>();
        }
    };

    public static class Game {
        String name;
        Scope<GameScope> scope;
        PlayerList pl;

        public Game(Scope scope, GameScope gs, PlayerList pl) {
            this.scope = scope;
            this.name = gs.name();
            this.pl = pl;
        }

        void addPlayer(Player player) {
            Scope<GamePlayerScope> gps = new Scope<>(new GamePlayerScope(player, this));
            gps.ownedBy(scope);
            gps.ownedBy(scope.findParent(new RootScope()).getChild(player));

            // 2 Owner pour GamePlayerScope!
            
            // La partie cool, instanciation automatique des classes;
            GamePlayer gp = gps.get(GamePlayer.class);

            pl.players.add(gp);
            
            increaseStat();
        }

        void increaseStat() {
            for (GamePlayer p : pl.players) {
                p.stat++;
                p.print();
            }
        }
    };

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

    @Test
    void testGamePlayer() {
        RootScope rs = new RootScope();
        Scope<RootScope> root = new Scope<>(rs);

        Player player = new Player("Joueur1");
        PlayerScope ps = new PlayerScope(player);
        ps.ownedBy(root);
        
        Player player2 = new Player("Joueur2");
        PlayerScope ps2 = new PlayerScope(player2);
        ps2.ownedBy(root);

        Scope<GameScope> g = new Scope<>(new GameScope("Jeu 1"));
        g.ownedBy(root);

        Game game = g.get(Game.class);
        game.addPlayer(player);
        game.addPlayer(player2);
    }



    
}
