package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



public class InjectTest {
    record RootScope(){};
    public static class A { static int i = 0; int j; public A() {
        j=++i;
    } };
    public static record B(A a){}

    public static record C(B b, String j) {};

    @Test
    void testInject() {
        A.i=0;
        Scope<RootScope> root = new Scope<>(new RootScope());

        Provider<A> a = root.provider(A.class);
        
        
        assertEquals(1, a.get().j);
        assertEquals(1, a.get().j);
        assertEquals(1, A.i);

        Provider<B> b = root.provider(B.class);

        assertEquals(b.get().a, a.get());
        assertEquals(1, A.i);
    }

    @Test
    void testInject2() {
        A.i=0;
        Scope<RootScope> root = new Scope<>(new RootScope());

        assertEquals(0, A.i);

        B b = root.get(B.class);
        
        assertEquals(1, A.i);
        assertEquals(1, b.a.j);

        B b2 = root.get(B.class);

        assertEquals(b, b2);

        assertEquals(1, root.get(A.class).j);
    }

    @Test
    void testInject3() {
        A.i=0;
        Scope<RootScope> root = new Scope<>(new RootScope());
        
        Provider<B> b = root.provider(B.class);

        assertEquals(0, A.i);

        b.get();
        
        assertEquals(1, A.i);

        b.get();
        assertEquals(1, A.i);
    }

    @Test
    void testInject4() {
        A.i=0;
        Scope<RootScope> root = new Scope<>(new RootScope());
        
        assertThrows(NoSuchBeanException.class, () -> root.provider(C.class));
        
        assertEquals(0, A.i);
        Provider<B> b = root.provider(B.class);
        assertEquals(0, A.i);
        b.get();
        assertEquals(1, A.i);
        b.get();
        assertEquals(1, A.i);
        assertEquals(1, b.get().a.j);
        assertEquals(b.get(), b.get());
    }
}
