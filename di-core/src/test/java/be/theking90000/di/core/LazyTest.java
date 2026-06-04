package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import be.theking90000.di.core.LazyTest.A1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



public class LazyTest {
    record RootScope(){};
    public static class A { static int i = 0; int j; public A() {
        j=++i;
    } };
    public static record B(Provider<A> a){}
    public static record B2(@Named("ok") Provider<A> a){};


    @Test
    void testInject() {
        A.i=0;
        Scope<RootScope> root = new Scope<>(new RootScope());

        Provider<B> b = root.provider(B.class);
        
        
        assertEquals(0, A.i);
        b.get();
        assertEquals(0,A.i);
        b.get().a().get();
        assertEquals(1, A.i);
        b.get().a().get();
        assertEquals(1, A.i);
        
        Provider<B2> b2 = root.provider(B2.class);
        assertEquals(1, A.i);
        b2.get();
        assertEquals(1,A.i);
        b2.get().a().get(); b.get();
        assertEquals(2, A.i);
        b2.get().a().get();
        assertEquals(2, A.i);
  
    }

    public record A1(Provider<A2> a2) { @Override
    public final String toString() {
        return "A1";
    }};
    public record A2(Provider<A1> a1) {@Override
    public final String toString() {
        return "A2";
    }};

    public record Counter(int i){};

    @Test
    void testCircular() {
        Scope<RootScope> root = new Scope<>(new RootScope());

        A1 a1 = root.get(A1.class);

        a1.a2.get();
        assertEquals(a1.a2.get().a1.get(), a1);
    }

    @Test
    void testMultiple() {
        Scope<RootScope> root = new Scope<>(new RootScope());
        
        for (int i = 0; i <10;i++)
            root.seed(Counter.class, new Counter(i));
        for (int i = 10; i<20;i++) {
            int j =i;
            root.provide(Counter.class, () -> new Counter(j));
        }

        assertThrows(AmbiguousException.class, ()->root.get(Counter.class));

        for (Provider<Counter> p : root.providers(Counter.class).get()) {
            System.out.println(p.get());
        }
    }

}
