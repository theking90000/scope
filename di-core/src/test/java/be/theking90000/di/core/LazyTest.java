package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

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

}
