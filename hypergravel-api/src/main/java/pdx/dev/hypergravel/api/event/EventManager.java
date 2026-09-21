package pdx.dev.hypergravel.api.event;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface EventManager {

    void register(Object owner, Object listener);

    <E> void register(Object owner, Class<E> eventType, PostOrder order, Consumer<E> handler);

    default <E> void register(Object owner, Class<E> eventType, Consumer<E> handler) {
        register(owner, eventType, PostOrder.NORMAL, handler);
    }

    void unregister(Object owner, Object listener);

    void unregisterAll(Object owner);

    <E> CompletableFuture<E> fire(E event);

    void fireAndForget(Object event);

    boolean hasListeners(Class<?> eventType);
}
