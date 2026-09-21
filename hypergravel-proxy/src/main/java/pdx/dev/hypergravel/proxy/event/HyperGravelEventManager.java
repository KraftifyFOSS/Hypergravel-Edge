package pdx.dev.hypergravel.proxy.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import pdx.dev.hypergravel.api.event.EventManager;
import pdx.dev.hypergravel.api.event.EventTask;
import pdx.dev.hypergravel.api.event.PostOrder;
import pdx.dev.hypergravel.api.event.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HyperGravelEventManager implements EventManager {

    private static final Logger LOGGER = LogManager.getLogger(HyperGravelEventManager.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Map<Class<?>, List<Registration>> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(Object owner, Object listener) {
        for (Method method : listener.getClass().getMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException(method + " must take exactly one event parameter");
            }
            Class<?> returnType = method.getReturnType();
            if (returnType != void.class && returnType != EventTask.class) {
                throw new IllegalArgumentException(
                        method + " must return void or EventTask, not " + returnType.getName());
            }

            Class<?> eventType = method.getParameterTypes()[0];
            MethodHandle handle;
            try {
                handle = LOOKUP.unreflect(method).bindTo(listener);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("cannot access " + method
                        + "; @Subscribe methods must be public", e);
            }
            add(eventType, new Registration(owner, listener, annotation.order(),
                    handle, returnType == EventTask.class));
        }
    }

    @Override
    public <E> void register(Object owner, Class<E> eventType, PostOrder order,
                             Consumer<E> handler) {
        add(eventType, new Registration(owner, handler, order, null, false) {
            @Override
            @SuppressWarnings("unchecked")
            Object invoke(Object event) {
                ((Consumer<E>) handler).accept((E) event);
                return null;
            }
        });
    }

    private void add(Class<?> eventType, Registration registration) {
        handlers.compute(eventType, (type, existing) -> {
            List<Registration> updated = existing == null
                    ? new ArrayList<>(1)
                    : new ArrayList<>(existing);
            updated.add(registration);
            updated.sort(Comparator.comparingInt(r -> r.order.ordinal()));
            return List.copyOf(updated);
        });
    }

    @Override
    public void unregister(Object owner, Object listener) {
        handlers.replaceAll((type, registrations) -> registrations.stream()
                .filter(r -> !(r.owner == owner && r.listener == listener))
                .toList());
    }

    @Override
    public void unregisterAll(Object owner) {
        handlers.replaceAll((type, registrations) -> registrations.stream()
                .filter(r -> r.owner != owner)
                .toList());
    }

    @Override
    public boolean hasListeners(Class<?> eventType) {
        List<Registration> registrations = handlers.get(eventType);
        return registrations != null && !registrations.isEmpty();
    }

    @Override
    public <E> CompletableFuture<E> fire(E event) {
        List<Registration> registrations = handlers.get(event.getClass());
        if (registrations == null || registrations.isEmpty()) {
            return CompletableFuture.completedFuture(event);
        }
        return dispatch(event, registrations, 0);
    }

    @Override
    public void fireAndForget(Object event) {
        List<Registration> registrations = handlers.get(event.getClass());
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        dispatch(event, registrations, 0);
    }

    private <E> CompletableFuture<E> dispatch(E event, List<Registration> registrations,
                                              int index) {
        for (int i = index; i < registrations.size(); i++) {
            Registration registration = registrations.get(i);
            Object result;
            try {
                result = registration.invoke(event);
            } catch (Throwable t) {

                LOGGER.error("handler {} threw on {}", registration.describe(),
                        event.getClass().getSimpleName(), t);
                continue;
            }

            if (result instanceof EventTask task) {
                int next = i + 1;
                CompletableFuture<Void> pending;
                try {
                    pending = task.execute();
                } catch (Throwable t) {
                    LOGGER.error("EventTask from {} threw immediately", registration.describe(), t);
                    continue;
                }
                return pending
                        .handle((ignored, error) -> {
                            if (error != null) {
                                LOGGER.error("EventTask from {} failed",
                                        registration.describe(), error);
                            }
                            return null;
                        })
                        .thenCompose(ignored -> dispatch(event, registrations, next));
            }
        }
        return CompletableFuture.completedFuture(event);
    }

    private static class Registration {

        final Object owner;
        final Object listener;
        final PostOrder order;
        private final MethodHandle handle;
        final boolean async;

        Registration(Object owner, Object listener, PostOrder order, MethodHandle handle,
                     boolean async) {
            this.owner = owner;
            this.listener = listener;
            this.order = order;
            this.handle = handle;
            this.async = async;
        }

        Object invoke(Object event) throws Throwable {
            return handle.invoke(event);
        }

        String describe() {
            return listener.getClass().getName();
        }
    }
}
