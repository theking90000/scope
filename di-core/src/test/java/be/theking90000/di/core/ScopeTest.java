package be.theking90000.di.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopeTest {

    record RootScope(){};

    record LanguageScope(String lang){};

    record Message(String message) {};

    @Test
    void createScope() {
       Scope<RootScope> root = new Scope<>(new RootScope());

       root.seed(Key.of(Message.class, "hello"), new Message("hello::ROOT"));

       Scope<LanguageScope> english = new Scope<>(new LanguageScope("english"));
       Scope<LanguageScope> french = new Scope<>(new LanguageScope("french"));

       english.ownedBy(root);
       french.ownedBy(root);
       
       Key<Message> k = Key.of(Message.class, "hello");

       english.seed(k, new Message("Hello"));
       french.seed(k, new Message("Bonjour"));
     
       assertEquals(french.provider(k).get().message(), "Bonjour");

       assertEquals(english.provider(k).get().message(), "Hello");

       final Scope<LanguageScope> quebec = new Scope<>(new LanguageScope("quebec"));
       assertThrows(NoSuchBeanException.class, () -> quebec.provider(k));
       
       quebec.ownedBy(french);

       assertEquals(quebec.provider(k).get().message(), "Bonjour");
       
       quebec.seed(k, new Message("Tabarnac"));

       assertEquals(quebec.provider(k).get().message(), "Tabarnac");

       // Il y a quelques mots partagé entre le québéquois et l'anglais

       quebec.ownedBy(english);
       assertEquals(quebec.provider(k).get().message(), "Tabarnac");

       quebec.close();
       final Scope<LanguageScope> quebec2 = new Scope<>(new LanguageScope("quebec"));

       quebec2.ownedBy(english);
       quebec2.ownedBy(french);

       assertThrows(AmbiguousException.class, () -> quebec2.provider(k));

       Key<Message> k2 = Key.of(Message.class, "word");
       english.seed(k2, new Message("Word"));

       assertEquals(quebec2.provider(k2).get().message(), "Word");

       root.seed(k2, new Message("root:Word"));
       assertEquals(french.provider(k2).get().message(), "root:Word");
       assertEquals(english.provider(k2).get().message(), "Word");
       assertThrows(AmbiguousException.class, () -> quebec2.provider(k2));

       quebec2.close();
    }

}
