# di-core

`di-core` est un conteneur de dépendances minimal centré sur une idée: une instance est créée dans un `Scope`, et ce scope définit ce qu'elle peut voir.

Le module fournit:

- `Scope<C>`: conteneur de durée de vie et de résolution;
- `Key<T>`: identifiant d'un bean par type + qualifier optionnel;
- `@Named`: qualifier pour distinguer plusieurs beans du même type;
- `Provider<T>`: dépendance lazy;
- `MultiProvider<T>`: collection de providers;
- injection automatique par constructeur public unique.

## Installation locale

Le projet est un module Gradle Java 21.

```bash
./gradlew :di-core:test
./gradlew :di-core:build
```

Package principal:

```java
package be.theking90000.di.core;
```

## Modele mental: scopes comme blocs de langage

Un `Scope` se lit comme un scope lexical dans JavaScript, Rust ou Java: une valeur definie dans un bloc externe est visible dans les blocs internes, sauf si le bloc interne definit une valeur plus proche avec la meme cle.

```java
// RootScope
{
    x = value;

    // PlayerScope
    {
        player = value;
        classes = ...;

        // Une classe creee ici voit:
        // - player et classes localement;
        // - x dans le parent;
        // - toute definition locale qui shadow une definition parente.
    }

    // GameScope
    {
        game = value;
        players = List<Scope<Player>>;

        // Player1Scope
        {
            player = player1;
        }

        // Player2Scope
        {
            player = player2;
        }
    }
}
```

Cette vue classique est un arbre: un enfant herite des providers visibles de son parent. La resolution par defaut utilise la definition la plus proche, appelee `NEAREST`. Comme pour une variable locale, une valeur locale shadow la valeur d'un parent.

## Premier exemple

```java
record RootScope() {}
record Config(String value) {}
record Service(Config config) {}

Scope<RootScope> root = new Scope<>(new RootScope());
root.seed(Config.class, new Config("prod"));

Service service = root.get(Service.class);
```

Quand `root.get(Service.class)` est appele:

1. `Scope` cherche un provider de `Service`.
2. S'il n'en trouve pas, il cree automatiquement `Service`.
3. L'injecteur inspecte le constructeur public unique de `Service`.
4. Il resout `Config`.
5. Il instancie `Service(config)`.
6. L'instance creee est cachee dans le scope comme singleton scope.

## API principale

### `new Scope<>(context)`

```java
record RootScope() {}

Scope<RootScope> root = new Scope<>(new RootScope());
```

Chaque scope seed automatiquement:

- son objet de contexte, ici `RootScope`;
- le scope lui-meme, sous `Scope.class`.

Donc une classe creee dans ce scope peut injecter:

```java
record NeedsScope(RootScope root, Scope<?> scope) {}
```

### `seed(type, instance)`

`seed` enregistre une instance deja creee.

```java
record Player(String name) {}
record PlayerService(Player player) {}

Scope<?> scope = new Scope<>(new Object());
Player player = new Player("Ada");

scope.seed(Player.class, player);

PlayerService service = scope.get(PlayerService.class);
assert service.player() == player;
```

Utiliser `seed` pour les objets possedes par du code externe: plugin, configuration, joueur, contexte serveur, handle natif, etc.

### `provide(type, provider)`

`provide` enregistre une fabrique.

```java
record Clock(long createdAt) {}

scope.provide(Clock.class, () -> new Clock(System.currentTimeMillis()));
```

Le provider est ensuite gere comme les autres providers du scope. Si un seul provider existe pour la cle, `get(...)` peut l'utiliser. Si plusieurs providers existent, `get(...)` echoue avec `AmbiguousBeanException`; utiliser `providers(...)` pour recuperer la collection.

### `bind(type)`

`bind` declare qu'un type doit etre cree automatiquement plus tard, sans verifier son graphe de dependances au moment du bind.

```java
interface JavaPlugin {}
record MyService(JavaPlugin plugin) {}

scope.bind(MyService.class);
scope.seed(JavaPlugin.class, plugin);

MyService service = scope.get(MyService.class);
```

C'est utile quand l'ordre d'enregistrement est important:

```java
// OK: le graphe n'est pas inspecte ici.
scope.bind(MyService.class);

// Plus tard, avant le premier get.
scope.seed(JavaPlugin.class, plugin);

// Le graphe est inspecte ici.
scope.get(MyService.class);
```

Sans `bind`, appeler `provider(MyService.class)` ou `get(MyService.class)` trop tot force la creation du graphe et peut tenter de creer localement une dependance pas encore enregistree.

### `get(type)` et `provider(type)`

`get` retourne l'instance:

```java
Service service = scope.get(Service.class);
```

`provider` retourne le provider:

```java
Provider<Service> service = scope.provider(Service.class);

// L'instance n'est creee qu'au get du provider.
Service value = service.get();
```

Dans le modele actuel, les providers crees automatiquement sont scopes comme singletons: plusieurs appels retournent la meme instance tant que le scope reste ouvert.

### `providers(type)`

`providers` retourne tous les providers les plus proches pour une cle.

```java
record Counter(int value) {}

scope.seed(Counter.class, new Counter(1));
scope.provide(Counter.class, () -> new Counter(2));

for (Provider<Counter> counter : scope.providers(Counter.class).get()) {
    System.out.println(counter.get().value());
}
```

Quand une classe a besoin de tous les providers d'un type, elle peut demander:

```java
record Counters(Provider<Iterable<Provider<Counter>>> counters) {
    int sum() {
        int result = 0;
        for (Provider<Counter> counter : counters.get()) {
            result += counter.get().value();
        }
        return result;
    }
}
```

Le type attendu est important:

```java
Provider<Iterable<Provider<Counter>>>
```

Cela signifie:

- `Provider<...>`: la collection est lazy;
- `Iterable<...>`: on demande plusieurs providers;
- `Provider<Counter>`: chaque element peut creer ou retourner un `Counter`.

## Injection par constructeur

Une classe injectable doit avoir exactement un constructeur public.

```java
public class Service {
    private final Repository repository;

    public Service(Repository repository) {
        this.repository = repository;
    }
}
```

Les records fonctionnent naturellement:

```java
public record Service(Repository repository) {}
```

Cas supportes dans les parametres de constructeur:

```java
TypeX value
Provider<TypeX> lazyValue
Provider<Iterable<Provider<TypeX>>> allProviders
@Named("main") TypeX namedValue
@Named("main") Provider<TypeX> namedLazyValue
```

Cas non supportes:

- classes locales ou anonymes;
- classes internes non statiques;
- type generique non concret comme `Provider<List<T>>`;
- classe sans constructeur public unique;
- cycle direct non lazy.

## Lazy et cycles

Un cycle direct ne peut pas etre construit:

```java
record C1(C2 c2) {}
record C2(C1 c1) {}

scope.get(C1.class); // BeanResolutionException
```

Un cycle lazy via `Provider<T>` est autorise:

```java
record A1(Provider<A2> a2) {}
record A2(Provider<A1> a1) {}

A1 a1 = scope.get(A1.class);
A2 a2 = a1.a2().get();

assert a2.a1().get() == a1;
```

La raison est simple: `Provider<T>` casse le besoin d'instancier immediatement l'autre cote du cycle.

## Qualifiers avec `Key` et `@Named`

`Key<T>` identifie un bean avec:

- un type;
- un qualifier optionnel.

```java
Key<Message> hello = Key.of(Message.class, "hello");
Key<Message> bye = Key.of(Message.class, "bye");

scope.seed(hello, new Message("Hello"));
scope.seed(bye, new Message("Bye"));
```

Injection avec `@Named`:

```java
record Greeter(@Named("hello") Message message) {}

Greeter greeter = scope.get(Greeter.class);
```

`bind` supporte aussi les keys qualifiees:

```java
Key<Service> serviceKey = Key.of(Service.class, "game");

scope.bind(serviceKey);
Service service = scope.get(serviceKey);
```

## Scopes parents et shadowing

Un scope peut voir ses parents visibles.

```java
record RootScope() {}
record Player(String name) {}
record Service(Player player, RootScope root) {}

Scope<RootScope> root = new Scope<>(new RootScope());

Scope<Player> playerScope = new Scope<>(new Player("Ada"));
playerScope.ownedBy(root);

Service service = playerScope.get(Service.class);
```

`Service` voit:

- `Player` dans `playerScope`;
- `RootScope` dans `root`;
- `Scope.class` shadowe par le scope le plus proche, donc `Scope<?>` injecte `playerScope`, pas `root`.

Si un enfant definit la meme cle qu'un parent, la valeur enfant gagne:

```java
root.seed(Config.class, new Config("root"));
playerScope.seed(Config.class, new Config("player"));

assert playerScope.get(Config.class).value().equals("player");
```

## Ownership, visibilite et cycle de vie

`attach(parent, owns, visible)` configure deux choses independantes:

- `owns`: le parent ferme ce scope quand il se ferme;
- `visible`: ce scope peut chercher des providers dans ce parent.

Helpers:

```java
child.ownedBy(parent); // owns=true, visible=true
child.weakRef(parent); // owns=false, visible=true
```

Quand un scope est ferme:

- ses enfants possedes sont fermes;
- ses providers locaux sont supprimes;
- il est detache des parents qui le possedent;
- les operations futures echouent avec `ScopeStateException`.

## Multi-parent scopes

Un scope n'est pas limite a un arbre strict. Il peut avoir plusieurs parents visibles, ce qui forme un graphe dirige acyclique.

Exemple: un joueur dans une partie peut vivre a l'intersection de deux contextes:

```java
record RootScope() {}
record Player(String name) {}
record GameScope(String name) {}
record GamePlayerScope(Player player, Game game) {}

Scope<RootScope> root = new Scope<>(new RootScope());

Scope<Player> globalPlayerScope = new Scope<>(new Player("Ada"));
globalPlayerScope.ownedBy(root);

Scope<GameScope> gameScope = new Scope<>(new GameScope("Arena"));
gameScope.ownedBy(root);

Scope<GamePlayerScope> gamePlayerScope =
    new Scope<>(new GamePlayerScope(new Player("Ada"), game));

gamePlayerScope.ownedBy(gameScope);
gamePlayerScope.ownedBy(globalPlayerScope);
```

Une classe creee dans `gamePlayerScope` peut voir:

- les valeurs locales du joueur dans la partie;
- les valeurs du `gameScope`;
- les valeurs du scope global du joueur;
- les valeurs du `root` si elles sont visibles via les parents.

C'est puissant pour modeliser des contextes croises: joueur + partie, requete + tenant, job + utilisateur, session + module.

## Ambiguite en multi-parent

La puissance du multi-parent vient avec un probleme: deux parents visibles peuvent fournir la meme cle.

```java
english.seed(Message.class, new Message("Hello"));
french.seed(Message.class, new Message("Bonjour"));

quebec.ownedBy(english);
quebec.ownedBy(french);

quebec.get(Message.class); // AmbiguousBeanException
```

Solutions possibles:

1. Shadow localement:

```java
quebec.seed(Message.class, new Message("Salut"));
quebec.get(Message.class); // "Salut"
```

2. Qualifier:

```java
english.seed(Key.of(Message.class, "en"), new Message("Hello"));
french.seed(Key.of(Message.class, "fr"), new Message("Bonjour"));
```

3. Demander tous les providers:

```java
for (Provider<Message> message : quebec.providers(Message.class).get()) {
    System.out.println(message.get().text());
}
```

## Resolution `NEAREST` et `DEEP`

Par defaut, `Scope` utilise `Collect.NEAREST`: sur chaque branche visible, la recherche s'arrete des qu'une definition est trouvee.

```java
scope.providers(Message.class); // NEAREST
scope.providers(Message.class, Scope.Collect.NEAREST);
```

`Collect.DEEP` continue a remonter apres une definition pour collecter aussi les providers plus hauts.

```java
scope.providers(Message.class, Scope.Collect.DEEP);
```

`NEAREST` correspond au modele de shadowing lexical. `DEEP` sert quand l'appelant veut inspecter ou agreger toutes les contributions visibles.

## Exceptions importantes

Toutes les exceptions DI heritent de `DiException`.

Resolution et creation:

- `BeanResolutionException`: base pour les erreurs de resolution;
- `NoSuchBeanException`: aucun provider disponible;
- `AmbiguousBeanException`: plusieurs providers possibles pour une resolution unique;
- `UnsupportedInjectionException`: type non injectable ou type generique non supporte;
- `BeanCreationException`: echec lors de l'instanciation reflective.

Scopes:

- `ScopeException`: base pour les erreurs de scope;
- `ScopeCycleException`: attacher un parent creerait un cycle dans le graphe;
- `ScopeConflictException`: conflit d'ownership avec un enfant deja ouvert;
- `ScopeStateException`: operation sur un scope ferme ou en fermeture.

## Recettes

### Declarer d'abord, fournir ensuite

```java
scope.bind(MyService.class);

// Plus tard:
scope.seed(JavaPlugin.class, plugin);

MyService service = scope.get(MyService.class);
```

### Injection lazy

```java
record Controller(Provider<Service> service) {
    void handle() {
        service.get().run();
    }
}
```

### Plusieurs implementations

```java
interface Command {}
record StartCommand() implements Command {}
record StopCommand() implements Command {}

scope.seed(Command.class, new StartCommand());
scope.seed(Command.class, new StopCommand());

record CommandRegistry(Provider<Iterable<Provider<Command>>> commands) {}
```

### Scope de joueur

```java
record RootScope() {}
record Player(String name) {}
record PlayerData(Player player, RootScope root, Scope<?> scope) {}

Scope<RootScope> root = new Scope<>(new RootScope());
Scope<Player> player = new Scope<>(new Player("Ada"));

player.ownedBy(root);

PlayerData data = player.get(PlayerData.class);
```

`data.player()` vient du scope joueur, `data.root()` vient du parent, et `data.scope()` est le scope joueur.

## Bonnes pratiques

- Utiliser `seed` pour les instances deja possedees par l'application.
- Utiliser `bind` quand un type doit etre cree plus tard et que ses dependances peuvent etre enregistrees apres.
- Utiliser `Provider<T>` pour casser les cycles ou retarder un cout d'instanciation.
- Utiliser `@Named` ou `Key.of(type, qualifier)` des qu'il y a plusieurs valeurs conceptuelles du meme type.
- Eviter les multi-parents avec les memes keys non qualifiees sauf si l'ambiguite est voulue et geree via `providers(...)`.
- Preferer des scopes petits et explicites: root, joueur, partie, requete, session, tenant, job.

