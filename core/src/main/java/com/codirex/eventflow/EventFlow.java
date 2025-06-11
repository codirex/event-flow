package com.codirex.eventflow;

import com.codirex.eventflow.DeadEvent;
import com.codirex.eventflow.annotation.Subscribe;
import com.codirex.eventflow.api.Cancelable;
import com.codirex.eventflow.api.ErrorContext;
import com.codirex.eventflow.api.ErrorHandler;
import com.codirex.eventflow.api.EventFlowIndex;
import com.codirex.eventflow.api.thread.BackgroundPoster;
import com.codirex.eventflow.api.thread.DefaultBackgroundPoster;
import com.codirex.eventflow.api.thread.MainThreadSupport;
import com.codirex.eventflow.spi.ReflectiveMethodExecutor;
import com.codirex.eventflow.spi.SubscriberInfoProvider;
import com.codirex.eventflow.spi.SubscriberMethodExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main class for the EventFlow library. Manages event subscriptions, posting, and delivery.
 * EventFlow allows loose coupling between components by dispatching events to subscribers.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Event posting and subscription.</li>
 *   <li>Different {@link ThreadMode}s for event delivery.</li>
 *   <li>Sticky events: New subscribers receive the last sticky event of a certain type.</li>
 *   <li>Subscriber prioritization.</li>
 *   <li>Optional annotation processor for optimized subscriber lookup (avoids reflection).</li>
 *   <li>Customizable error handling.</li>
 *   <li>Support for Android main thread.</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b>
 * <pre>{@code
 * // Get default instance
 * EventFlow eventFlow = EventFlow.getDefault();
 *
 * // Register a subscriber
 * eventFlow.register(this);
 *
 * // Post an event
 * eventFlow.post(new MyEvent());
 *
 * // Unregister subscriber
 * eventFlow.unregister(this);
 *
 * // Subscriber method
 * @Subscribe
 * public void onMyEvent(MyEvent event) {
 *     // Handle event
 * }
 * }</pre>
 *
 * @see Builder
 * @see Subscribe
 */
public class EventFlow {
    private static volatile EventFlow defaultInstance;

    private final MainThreadSupport mainThreadSupport;
    private final BackgroundPoster backgroundPoster;
    private final ExecutorService asyncExecutorService;
    private final ErrorHandler errorHandler;

    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean strictMode;
    private final boolean asyncExecutorProvidedByBuilder;
    private final boolean backgroundPosterProvidedByBuilder;

    private EventFlowIndex EventFlowIndex;
    private boolean useIndex = true;
    private static final String GENERATED_INDEX_CLASS_NAME =
            "com.example.EventFlow.generated.MyEventFlowIndex";

    private final Map<Class<?>, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Class<?>>> eventTypesCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> stickyEvents = new ConcurrentHashMap<>();
    private volatile boolean isShutdown = false;

    EventFlow(Builder builder) {
        this.mainThreadSupport = builder.mainThreadSupport;
        this.errorHandler = builder.errorHandler;

        this.asyncExecutorProvidedByBuilder = builder.asyncExecutorService != null;
        if (this.asyncExecutorProvidedByBuilder) {
            this.asyncExecutorService = builder.asyncExecutorService;
        } else {
            this.asyncExecutorService =
                    Executors.newCachedThreadPool(
                            r -> {
                                Thread t = new Thread(r, "EventFlow-DefaultAsync");
                                t.setDaemon(true);
                                return t;
                            });
        }

        this.backgroundPosterProvidedByBuilder = builder.backgroundPoster != null;
        if (this.backgroundPosterProvidedByBuilder) {
            this.backgroundPoster = builder.backgroundPoster;
        } else {
            this.backgroundPoster = new DefaultBackgroundPoster();
        }

        this.logSubscriberExceptions = builder.logSubscriberExceptions;
        this.logNoSubscriberMessages = builder.logNoSubscriberMessages;
        this.sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        this.sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        this.strictMode = builder.strictMode;

        try {
            Class<?> indexClazz = Class.forName(GENERATED_INDEX_CLASS_NAME);
            this.EventFlowIndex =
                    (EventFlowIndex) indexClazz.getDeclaredConstructor().newInstance();

        } catch (Exception e) {

            if (this.logNoSubscriberMessages) {
                System.err.println(
                        "EventFlow: Annotation processor index "
                                + GENERATED_INDEX_CLASS_NAME
                                + " not found or failed to load. Will use reflection. "
                                + e.getMessage());
            }
        }
    }

    /**
     * Private constructor for creating an EventFlow instance without a builder, used for the default instance.
     * Initializes with default settings.
     */
    private EventFlow() {

        this(new Builder());
    }

    /**
     * Returns the default {@link EventFlow} instance. If it doesn't exist, it's created with default settings.
     * This method is thread-safe.
     *
     * @return The default {@link EventFlow} instance.
     */
    public static EventFlow getDefault() {
        if (defaultInstance == null) {
            synchronized (EventFlow.class) {
                if (defaultInstance == null) {
                    defaultInstance = new Builder().build();
                }
            }
        }
        return defaultInstance;
    }

    /**
     * Registers the given subscriber object to receive events.
     * Subscriber methods are identified by the {@link Subscribe} annotation.
     * If an {@link EventFlowIndex} is available and configured, it will be used for faster subscriber method lookup.
     * Otherwise, reflection will be used.
     * <p>
     * If {@link Builder#strictMode(boolean)} is enabled, this method will throw exceptions for invalid subscribers
     * (e.g., null, or methods not conforming to rules like being public and having one parameter).
     * Otherwise, warnings are logged.
     * <p>
     * If the subscriber is already registered, this call might be ignored or log a warning,
     * depending on the internal state and strict mode.
     * <p>
     * If the EventFlow instance has been {@link #shutdown()}, registration attempts will be ignored or cause an exception
     * in strict mode.
     *
     * @param subscriber The subscriber object. Must not be null.
     * @throws IllegalArgumentException if subscriber is null and strict mode is enabled.
     * @throws EventFlowException if a subscriber method is invalid (e.g., not public, wrong parameters)
     *                            and strict mode is enabled.
     * @throws IllegalStateException if EventFlow is shut down and strict mode is enabled.
     */
    public void register(Object subscriber) {
        if (this.isShutdown) {
            String message =
                    "EventFlow ["
                            + this
                            + "] has been shut down. Cannot register subscriber: "
                            + (subscriber != null ? subscriber.getClass().getName() : "null");
            if (this.strictMode) {
                throw new IllegalStateException(message);
            }
            if (this.logSubscriberExceptions) {

                System.err.println("EventFlow: Warning - " + message);
            }
            return;
        }
        if (subscriber == null) {
            if (this.strictMode) {
                throw new IllegalArgumentException("Subscriber to register must not be null.");
            } else {
                System.err.println(
                        "EventFlow: Warning - Attempted to register a null subscriber. Ignoring.");
                return;
            }
        }

        Class<?> subscriberClass = subscriber.getClass();

        if (useIndex && EventFlowIndex != null) {

            List<SubscriberInfoProvider> providers =
                    this.EventFlowIndex.getSubscriberInfoProviders(subscriberClass);

            if (providers != null && !providers.isEmpty()) {
                for (SubscriberInfoProvider provider : providers) {
                    if (provider == null || provider.getEventType() == null) {
                        String errorMessage =
                                "Encountered null provider or null event type from index for "
                                        + subscriberClass.getName();
                        if (this.strictMode) {
                            throw new EventFlowException(errorMessage);
                        }
                        if (this.logSubscriberExceptions) {

                            System.err.println(
                                    "EventFlow: Warning - "
                                            + errorMessage
                                            + ". Skipping provider.");
                        }
                        continue;
                    }

                    Subscription subscription = new Subscription(subscriber, provider);

                    if (provider.isSticky()) {
                        deliverStickyEventsToSubscription(subscription);
                    }
                    addSubscriptionToMap(provider.getEventType(), subscription);
                }
                return;
            } else if (providers != null) {

                System.out.println(
                        "EventFlow: Index indicates no subscriber methods for "
                                + subscriberClass.getName());
                return;
            }

            System.out.println(
                    "EventFlow: Index does not know "
                            + subscriberClass.getName()
                            + ". Falling back to reflection.");
        }

        System.out.println("EventFlow: Using reflection to register " + subscriberClass.getName());
        Method[] methods = subscriberClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                int methodModifiers = method.getModifiers();
                if (!Modifier.isPublic(methodModifiers)) {
                    String errorMessage =
                            "Subscriber method "
                                    + method.getName()
                                    + " in "
                                    + subscriberClass.getName()
                                    + " is not public.";
                    if (this.strictMode) {
                        throw new EventFlowException(errorMessage + " (Strict mode enabled)");
                    }
                    System.err.println("EventFlow: " + errorMessage + " Skipping.");
                    continue;
                }
                if (method.getParameterTypes().length != 1) {
                    String errorMessage =
                            "Subscriber method "
                                    + method.getName()
                                    + " in "
                                    + subscriberClass.getName()
                                    + " must have exactly one parameter.";
                    if (this.strictMode) {
                        throw new EventFlowException(errorMessage + " (Strict mode enabled)");
                    }
                    System.err.println("EventFlow: " + errorMessage + " Skipping.");
                    continue;
                }

                Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                Class<?> eventType = method.getParameterTypes()[0];
                SubscriberMethod subscriberMethodInfo =
                        new SubscriberMethod(
                                method,
                                eventType,
                                subscribeAnnotation.threadMode(),
                                subscribeAnnotation.priority(),
                                subscribeAnnotation.sticky());
                Subscription subscription = new Subscription(subscriber, subscriberMethodInfo);

                if (subscriberMethodInfo.isSticky()) {
                    deliverStickyEventsToSubscription(subscription);
                }
                addSubscriptionToMap(subscriberMethodInfo.getEventType(), subscription);
            }
        }
    }

    private void addSubscriptionToMap(Class<?> eventType, Subscription subscription) {
        List<Subscription> currentSubscriptions =
                subscriptions.computeIfAbsent(eventType, ev -> new CopyOnWriteArrayList<>());

        if (currentSubscriptions.contains(subscription)) {

            System.err.println(
                    "Subscriber "
                            + subscription.getSubscriber().getClass().getName()
                            + " method "
                            + ((SubscriberMethod)subscription.getInfoProvider()).getMethod().getName()
                            + " already registered for event type "
                            + eventType.getName());
            return;
        }

        currentSubscriptions.add(subscription);

        Collections.sort(
                currentSubscriptions,
                (s1, s2) ->
                        Integer.compare(
                                s2.getPriority(),
                                s1.getPriority()));
    }

    /**
     * Unregisters the given subscriber object, so it will no longer receive events.
     * If the subscriber was not registered, or is null, this method may log a warning or,
     * in strict mode, throw an {@link IllegalArgumentException}.
     * <p>
     * If the EventFlow instance has been {@link #shutdown()}, unregistration attempts will be ignored or cause an exception
     * in strict mode.
     *
     * @param subscriber The subscriber object to unregister. Must not be null.
     * @throws IllegalArgumentException if subscriber is null and strict mode is enabled.
     * @throws IllegalStateException if EventFlow is shut down and strict mode is enabled.
     */
    public void unregister(Object subscriber) {
        if (this.isShutdown) {
            String message =
                    "EventFlow ["
                            + this
                            + "] has been shut down. Cannot unregister subscriber: "
                            + (subscriber != null ? subscriber.getClass().getName() : "null");
            if (this.strictMode) {
                throw new IllegalStateException(message);
            }
            if (this.logSubscriberExceptions) {
                System.err.println("EventFlow: Warning - " + message);
            }
            return;
        }
        if (subscriber == null) {
            if (this.strictMode) {
                throw new IllegalArgumentException("Subscriber to unregister must not be null.");
            } else {
                System.err.println(
                        "EventFlow: Warning - Attempted to unregister a null subscriber. Ignoring.");
                return;
            }
        }

        boolean removedAny = false;
        for (Map.Entry<Class<?>, List<Subscription>> entry : subscriptions.entrySet()) {
            List<Subscription> eventSubscriptions = entry.getValue();
            if (eventSubscriptions != null && !eventSubscriptions.isEmpty()) {
                Iterator<Subscription> iterator = eventSubscriptions.iterator();
                while (iterator.hasNext()) {
                    Subscription subscription = iterator.next();
                    if (subscription.getSubscriber() == subscriber) {
                        iterator.remove();
                        removedAny = true;
                    }
                }
            }
        }
        if (removedAny && this.logNoSubscriberMessages) {

            System.out.println(
                    "EventFlow: Unregistered subscriber: " + subscriber.getClass().getName());
        }
    }

    /**
     * Posts the given event to all registered subscribers that are interested in this event type.
     * The event will be delivered to subscriber methods based on their specified {@link ThreadMode}.
     * <p>
     * If no subscribers are found for the event type and {@link Builder#sendNoSubscriberEvent(boolean)} is true (default),
     * a {@link DeadEvent} wrapping the original event will be posted.
     * <p>
     * If the event is an instance of {@link Cancelable} and {@link Cancelable#isCanceled()} is true,
     * further dispatching to subscribers might be halted for that event.
     * <p>
     * If the event is null and strict mode is enabled, an {@link IllegalArgumentException} is thrown.
     * Otherwise, a warning is logged and the event is ignored.
     * <p>
     * If the EventFlow instance has been {@link #shutdown()}, posting attempts will be ignored or cause an exception
     * in strict mode.
     *
     * @param event The event object to post. Must not be null.
     * @throws IllegalArgumentException if event is null and strict mode is enabled.
     * @throws IllegalStateException if EventFlow is shut down and strict mode is enabled.
     * @throws EventFlowException if there's an issue with thread support (e.g., {@link MainThreadSupport} not available
     *                            for {@link ThreadMode#MAIN}) and strict mode is enabled.
     */
    public void post(Object event) {
        if (this.isShutdown) {
            String message =
                    "EventFlow ["
                            + this
                            + "] has been shut down. Cannot post event: "
                            + (event != null ? event.getClass().getName() : "null");
            if (this.strictMode) {
                throw new IllegalStateException(message);
            }
            if (this.logNoSubscriberMessages) {
                System.err.println("EventFlow: Warning - " + message);
            }
            return;
        }
        if (event == null) {
            if (this.strictMode) {
                throw new IllegalArgumentException("Event to post must not be null.");
            }
            if (this.logNoSubscriberMessages) {

                System.err.println("EventFlow: Warning - Null event posted. Ignoring.");
            }
            return;
        }

        boolean dispatchedAtLeastOnce = false;
        Class<?> eventClass = event.getClass();
        List<Class<?>> eventHierarchy = lookupEventTypes(eventClass);

        if (eventHierarchy.isEmpty()) {

            if (this.logNoSubscriberMessages) {
                System.err.println(
                        "EventFlow: No event types found for event: "
                                + eventClass.getName()
                                + ". This indicates an issue with type lookup.");
            }
            return;
        }

        for (Class<?> currentEventType : eventHierarchy) {
            List<Subscription> eventSubscriptions = subscriptions.get(currentEventType);

            if (eventSubscriptions != null && !eventSubscriptions.isEmpty()) {
                for (Subscription subscription : eventSubscriptions) {
                    if (event instanceof Cancelable c && c.isCanceled()) {
                        break;
                    }

                    final Subscription finalSubscription = subscription;
                    final Object finalEvent = event;

                    Runnable task =
                            () -> {
                                if (finalEvent instanceof Cancelable c && c.isCanceled()) {
                                    return;
                                }
                                invokeSubscriber(finalSubscription, finalEvent);
                            };

                    ThreadMode threadMode = subscription.getInfoProvider().getThreadMode();
                    switch (threadMode) {
                        case POSTING:
                            task.run();
                            break;
                        case MAIN:
                            if (this.mainThreadSupport != null) {
                                this.mainThreadSupport.postToMainThread(task);
                            } else {
                                String msg =
                                        "MainThreadSupport not installed, required for ThreadMode.MAIN when posting event: "
                                                + finalEvent.getClass().getSimpleName();
                                if (this.strictMode) throw new EventFlowException(msg);
                                if (this.logSubscriberExceptions)
                                    System.err.println("EventFlow: Error - " + msg);
                            }
                            break;
                        case BACKGROUND:
                            if (this.backgroundPoster != null) {
                                this.backgroundPoster.enqueue(task);
                            } else {
                                String msg =
                                        "BackgroundPoster not installed, required for ThreadMode.BACKGROUND when posting event: "
                                                + finalEvent.getClass().getSimpleName();
                                if (this.strictMode) throw new EventFlowException(msg);
                                if (this.logSubscriberExceptions)
                                    System.err.println("EventFlow: Error - " + msg);
                            }
                            break;
                        case ASYNC:
                            if (this.asyncExecutorService != null) {
                                this.asyncExecutorService.execute(task);
                            } else {
                                String msg =
                                        "ExecutorService (for ASYNC) not installed, required for ThreadMode.ASYNC when posting event: "
                                                + finalEvent.getClass().getSimpleName();
                                if (this.strictMode) throw new EventFlowException(msg);
                                if (this.logSubscriberExceptions)
                                    System.err.println("EventFlow: Error - " + msg);
                            }
                            break;
                        default:
                            if (this.logSubscriberExceptions)
                                System.err.println(
                                        "EventFlow: Error - Unknown ThreadMode "
                                                + threadMode
                                                + " for event "
                                                + finalEvent.getClass().getSimpleName()
                                                + ". Falling back to POSTING.");
                            task.run();
                            break;
                    }
                    dispatchedAtLeastOnce = true;
                }
            }
        }

        if (!dispatchedAtLeastOnce && !(event instanceof DeadEvent) && this.sendNoSubscriberEvent) {
            if (this.logNoSubscriberMessages) {
                System.out.println(
                        "EventFlow: No subscribers found for event "
                                + event.getClass().getName()
                                + ". Posting DeadEvent.");
            }
            post(new DeadEvent(event));
        }
    }

    /**
     * Posts the given event as a "sticky" event. Sticky events are stored, and any new subscriber
     * that registers for this event type (or a supertype) and has a sticky subscriber method
     * will immediately receive the last posted sticky event of that type.
     * <p>
     * After being stored, the event is also posted to currently registered subscribers like a normal event via {@link #post(Object)}.
     * If an existing sticky event of the same type exists, it is replaced.
     * <p>
     * If the event is null and strict mode is enabled, an {@link IllegalArgumentException} is thrown.
     * Otherwise, a warning is logged and the event is ignored.
     * <p>
     * If the EventFlow instance has been {@link #shutdown()}, posting attempts will be ignored or cause an exception
     * in strict mode.
     *
     * @param event The sticky event object to post. Must not be null.
     * @throws IllegalArgumentException if event is null and strict mode is enabled.
     * @throws IllegalStateException if EventFlow is shut down and strict mode is enabled.
     */
    public void postSticky(Object event) {
        if (this.isShutdown) {
            String message =
                    "EventFlow ["
                            + this
                            + "] has been shut down. Cannot postSticky event: "
                            + (event != null ? event.getClass().getName() : "null");
            if (this.strictMode) {
                throw new IllegalStateException(message);
            }
            if (this.logNoSubscriberMessages) {
                System.err.println("EventFlow: Warning - " + message);
            }
            return;
        }
        if (event == null) {
            if (this.strictMode) {
                throw new IllegalArgumentException("Sticky event to post must not be null.");
            }
            if (this.logNoSubscriberMessages) {

                System.err.println("EventFlow: Warning - Null sticky event posted. Ignoring.");
            }
            return;
        }

        stickyEvents.put(event.getClass(), event);

        post(event);
    }

    /**
     * Retrieves the last posted sticky event of the given {@code eventType}.
     *
     * @param eventType The class of the sticky event to retrieve. Must not be null.
     * @param <T> The type of the event.
     * @return The sticky event of the specified type, or {@code null} if no such sticky event exists or if eventType is null.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStickyEvent(Class<T> eventType) {
        if (eventType == null) {
            if (this.logNoSubscriberMessages) {
                System.err.println(
                        "EventFlow: Warning - eventType cannot be null for getStickyEvent. Returning null.");
            }
            return null;
        }

        Object event = stickyEvents.get(eventType);
        if (event != null && eventType.isInstance(event)) {
            return (T) event;
        }
        return null;
    }

    /**
     * Removes and returns the sticky event of the given {@code eventType}.
     * Subsequent calls to {@link #getStickyEvent(Class)} for this type will return {@code null}
     * until a new sticky event of this type is posted.
     *
     * @param eventType The class of the sticky event to remove. Must not be null.
     * @param <T> The type of the event.
     * @return The removed sticky event, or {@code null} if no such sticky event existed or if eventType is null.
     */
    @SuppressWarnings("unchecked")
    public <T> T removeStickyEvent(Class<T> eventType) {
        if (eventType == null) {
            if (this.logNoSubscriberMessages) {
                System.err.println(
                        "EventFlow: Warning - eventType cannot be null for removeStickyEvent. Returning null.");
            }
            return null;
        }

        Object event = stickyEvents.remove(eventType);
        if (event != null && eventType.isInstance(event)) {
            return (T) event;
        }
        return null;
    }

    /**
     * Removes all sticky events of all types.
     */
    public void removeAllStickyEvents() {
        stickyEvents.clear();
    }

    private void invokeSubscriber(Subscription subscription, Object event) {
        Object subscriber = subscription.getSubscriber();
        SubscriberInfoProvider provider = subscription.getInfoProvider();

        if (provider == null) {
            String msg =
                    "SubscriberInfoProvider not found for subscriber "
                            + (subscriber != null ? subscriber.getClass().getName() : "null")
                            + ". Cannot invoke.";
            if (this.strictMode) throw new EventFlowException(msg);
            if (this.logSubscriberExceptions) System.err.println("EventFlow: Error - " + msg);
            return;
        }

        SubscriberMethodExecutor executor = provider.getMethodExecutor();
        if (executor == null) {
            String msg =
                    "SubscriberMethodExecutor not found for "
                            + (subscriber != null ? subscriber.getClass().getName() : "null")
                            + " event "
                            + (event != null ? event.getClass().getName() : "null")
                            + ". Cannot invoke.";
            if (this.strictMode) throw new EventFlowException(msg);
            if (this.logSubscriberExceptions) System.err.println("EventFlow: Error - " + msg);
            return;
        }

        Method originalMethodForContext = null;
        if (executor instanceof ReflectiveMethodExecutor exec) {
            originalMethodForContext = exec.getMethod();
        }

        try {
            executor.invoke(subscriber, event);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (this.errorHandler != null) {
                ErrorContext context =
                        new DefaultErrorContextImpl(
                                this,
                                event,
                                subscriber,
                                originalMethodForContext,
                                provider.getSubscriberMethodName());
                try {
                    this.errorHandler.handleError(targetException, context);
                } catch (Throwable errorHandlerException) {
                    if (this.logSubscriberExceptions) {
                        System.err.println(
                                "EventFlow: Exception thrown by globalErrorHandler itself:");
                        errorHandlerException.printStackTrace(System.err);
                        System.err.println(
                                "EventFlow: Original subscriber exception (from InvocationTargetException) was:");
                        targetException.printStackTrace(System.err);
                    }
                }
            } else {
                if (this.logSubscriberExceptions) {
                    System.err.println(
                            "EventFlow: Exception thrown by subscriber "
                                    + subscriber.getClass().getName()
                                    + " method "
                                    + provider.getSubscriberMethodName()
                                    + " for event "
                                    + event.getClass().getName()
                                    + ": "
                                    + targetException);
                    targetException.printStackTrace(System.err);
                }
            }
        } catch (EventFlowException e) {
            if (this.errorHandler != null) {
                ErrorContext context =
                        new DefaultErrorContextImpl(
                                this,
                                event,
                                subscriber,
                                originalMethodForContext,
                                provider.getSubscriberMethodName());
                try {
                    this.errorHandler.handleError(e, context);
                } catch (Throwable errorHandlerException) {
                    if (this.logSubscriberExceptions) {
                        System.err.println(
                                "EventFlow: Exception thrown by globalErrorHandler itself (handling EventFlowException):");
                        errorHandlerException.printStackTrace(System.err);
                        System.err.println("EventFlow: Original EventFlowException was:");
                        e.printStackTrace(System.err);
                    }
                }
            } else {
                if (this.logSubscriberExceptions) {
                    System.err.println(
                            "EventFlow: Internal EventFlowException during event dispatch to "
                                    + subscriber.getClass().getName()
                                    + " (method "
                                    + provider.getSubscriberMethodName()
                                    + ")"
                                    + " for event "
                                    + event.getClass().getName()
                                    + ": "
                                    + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        } catch (RuntimeException e) {
            if (this.errorHandler != null) {
                ErrorContext context =
                        new DefaultErrorContextImpl(
                                this,
                                event,
                                subscriber,
                                originalMethodForContext,
                                provider.getSubscriberMethodName());
                try {
                    this.errorHandler.handleError(e, context);
                } catch (Throwable errorHandlerException) {
                    if (this.logSubscriberExceptions) {
                        System.err.println(
                                "EventFlow: Exception thrown by globalErrorHandler itself (handling RuntimeException):");
                        errorHandlerException.printStackTrace(System.err);
                        System.err.println("EventFlow: Original RuntimeException was:");
                        e.printStackTrace(System.err);
                    }
                }
            } else {
                if (this.logSubscriberExceptions) {
                    System.err.println(
                            "EventFlow: Unexpected RuntimeException during event dispatch to "
                                    + subscriber.getClass().getName()
                                    + " (method "
                                    + provider.getSubscriberMethodName()
                                    + ")"
                                    + " for event "
                                    + event.getClass().getName()
                                    + ": "
                                    + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    private List<Class<?>> lookupEventTypes(Class<?> eventClass) {
        if (eventClass == null) {
            return Collections.emptyList();
        }

        List<Class<?>> cachedTypes = eventTypesCache.get(eventClass);
        if (cachedTypes != null) {
            return cachedTypes;
        }

        List<Class<?>> eventHierarchy = new ArrayList<>();
        Set<Class<?>> processedClasses = new HashSet<>();

        Queue<Class<?>> queue = new LinkedList<>();
        queue.add(eventClass);

        while (!queue.isEmpty()) {
            Class<?> currentClass = queue.poll();
            if (currentClass == null || processedClasses.contains(currentClass)) {
                continue;
            }

            eventHierarchy.add(currentClass);
            processedClasses.add(currentClass);

            Class<?> superclass = currentClass.getSuperclass();
            if (superclass != null) {
                if (!processedClasses.contains(superclass)) {
                    queue.add(superclass);
                }
            }

            for (Class<?> anInterface : currentClass.getInterfaces()) {
                if (!processedClasses.contains(anInterface)) {
                    queue.add(anInterface);
                }
            }
        }

        eventTypesCache.put(eventClass, Collections.unmodifiableList(eventHierarchy));
        return eventHierarchy;
    }

    private void deliverStickyEventsToSubscription(final Subscription subscription) {
        for (Map.Entry<Class<?>, Object> entry : stickyEvents.entrySet()) {
            Class<?> stickyEventType = entry.getKey();
            final Object stickyEvent = entry.getValue();

            Class<?> subscriberEventType = subscription.getEventType();

            if (subscriberEventType.isAssignableFrom(stickyEventType)) {
                final Runnable task = () -> invokeSubscriber(subscription, stickyEvent);

                ThreadMode threadMode = subscription.getInfoProvider().getThreadMode();

                switch (threadMode) {
                    case POSTING:
                        task.run();

                        break;
                    case MAIN:
                        if (this.mainThreadSupport != null) {
                            this.mainThreadSupport.postToMainThread(task);
                        } else {
                            String msg =
                                    "MainThreadSupport not installed, required for ThreadMode.MAIN for sticky event: "
                                            + stickyEvent.getClass().getSimpleName();
                            if (this.strictMode) throw new EventFlowException(msg);
                            if (this.logSubscriberExceptions)
                                System.err.println("EventFlow: Error - " + msg);
                        }
                        break;
                    case BACKGROUND:
                        if (this.backgroundPoster != null) {
                            this.backgroundPoster.enqueue(task);
                        } else {
                            String msg =
                                    "BackgroundPoster not installed, required for ThreadMode.BACKGROUND for sticky event: "
                                            + stickyEvent.getClass().getSimpleName();
                            if (this.strictMode) throw new EventFlowException(msg);
                            if (this.logSubscriberExceptions)
                                System.err.println("EventFlow: Error - " + msg);
                        }
                        break;
                    case ASYNC:
                        if (this.asyncExecutorService != null) {
                            this.asyncExecutorService.execute(task);
                        } else {
                            String msg =
                                    "ExecutorService (for ASYNC) not installed, required for ThreadMode.ASYNC for sticky event: "
                                            + stickyEvent.getClass().getSimpleName();
                            if (this.strictMode) throw new EventFlowException(msg);
                            if (this.logSubscriberExceptions)
                                System.err.println("EventFlow: Error - " + msg);
                        }
                        break;
                    default:
                        if (this.logSubscriberExceptions)
                            System.err.println(
                                    "EventFlow: Error - Unknown ThreadMode "
                                            + threadMode
                                            + " for sticky event "
                                            + stickyEvent.getClass().getSimpleName()
                                            + ". Falling back to POSTING.");
                        task.run();
                        break;
                }
            }
        }
    }

    /**
     * Shuts down this EventFlow instance. After shutdown, no new subscribers can be registered,
     * and no new events can be posted. Attempts to do so may be ignored or, in strict mode,
     * throw an {@link IllegalStateException}.
     * <p>
     * This method also attempts to shut down any internally managed {@link ExecutorService} (for ASYNC tasks)
     * and {@link BackgroundPoster} (if they were not provided by the builder).
     * If these services were provided via the {@link Builder}, their lifecycle management remains
     * the responsibility of the caller.
     * <p>
     * Calling shutdown multiple times has no additional effect.
     */
    public void shutdown() {
        if (!this.isShutdown) {
            this.isShutdown = true;

            if (!this.asyncExecutorProvidedByBuilder && this.asyncExecutorService != null) {

                this.asyncExecutorService.shutdown();
            }

            if (!this.backgroundPosterProvidedByBuilder && this.backgroundPoster != null) {
                if (this.backgroundPoster instanceof DefaultBackgroundPoster) {

                    ((DefaultBackgroundPoster) this.backgroundPoster).shutdown();
                } else {
                    System.err.println(
                            "EventFlow: Warning - Default backgroundPoster is not an instance of DefaultBackgroundPoster during shutdown for "
                                    + this
                                    + ". Type: "
                                    + this.backgroundPoster.getClass().getName());
                }
            }
        }
    }

    /**
     * Builder for {@link EventFlow} instances. Allows configuration of various aspects
     * like error handling, executor services, and logging behavior.
     */
    public static class Builder {
        ErrorHandler errorHandler;
        ExecutorService asyncExecutorService;
        BackgroundPoster backgroundPoster;
        MainThreadSupport mainThreadSupport;

        boolean logSubscriberExceptions = true;
        boolean logNoSubscriberMessages = true;
        boolean sendSubscriberExceptionEvent = false;
        boolean sendNoSubscriberEvent = true;
        boolean strictMode = false;

        /**
         * Default constructor for the Builder.
         */
        public Builder() {}

        /**
         * Sets the {@link ErrorHandler} to be used by the EventFlow instance.
         * The error handler is invoked when a subscriber method throws an exception
         * or when an internal error occurs during event dispatch.
         *
         * @param handler The error handler.
         * @return This Builder instance for chaining.
         */
        public Builder errorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
            return this;
        }

        /**
         * Sets the {@link ExecutorService} to be used for {@link ThreadMode#ASYNC} event delivery.
         * If not provided, EventFlow will create a default cached thread pool.
         * If provided, the lifecycle of this executor service is managed externally.
         *
         * @param executorService The executor service for async tasks.
         * @return This Builder instance for chaining.
         */
        public Builder asyncExecutorService(ExecutorService executorService) {
            this.asyncExecutorService = executorService;
            return this;
        }

        /**
         * Sets the {@link BackgroundPoster} to be used for {@link ThreadMode#BACKGROUND} event delivery.
         * If not provided, EventFlow will use a {@link DefaultBackgroundPoster}.
         * If provided, the lifecycle of this poster is managed externally (unless it's the default one and not provided by builder).
         *
         * @param backgroundPoster The background poster.
         * @return This Builder instance for chaining.
         */
        public Builder backgroundPoster(BackgroundPoster backgroundPoster) {
            this.backgroundPoster = backgroundPoster;
            return this;
        }

        /**
         * Sets the {@link MainThreadSupport} instance, required for delivering events on the main thread
         * (typically the UI thread in Android applications) using {@link ThreadMode#MAIN}.
         *
         * @param mainThreadSupport The main thread support implementation.
         * @return This Builder instance for chaining.
         */
        public Builder mainThreadSupport(MainThreadSupport mainThreadSupport) {
            this.mainThreadSupport = mainThreadSupport;
            return this;
        }

        /**
         * Configures whether EventFlow should log exceptions thrown by subscriber methods.
         * Default is true.
         *
         * @param log True to log exceptions, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder logSubscriberExceptions(boolean log) {
            this.logSubscriberExceptions = log;
            return this;
        }

        /**
         * Configures whether EventFlow should log messages when an event is posted for which
         * no subscribers are registered. Default is true.
         *
         * @param log True to log such messages, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder logNoSubscriberMessages(boolean log) {
            this.logNoSubscriberMessages = log;
            return this;
        }

        /**
         * Configures whether EventFlow should post a {@link DeadEvent} if an event is posted
         * for which an exception occurs in a subscriber. This is distinct from {@link #sendNoSubscriberEvent(boolean)}.
         * Default is false.
         *
         * @param send True to post a DeadEvent on subscriber exception, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder sendSubscriberExceptionEvent(boolean send) {
            this.sendSubscriberExceptionEvent = send;
            return this;
        }

        /**
         * Configures whether EventFlow should post a {@link DeadEvent} if an event is posted
         * for which no subscribers are found. Default is true.
         *
         * @param send True to post a DeadEvent when no subscribers are found, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder sendNoSubscriberEvent(boolean send) {
            this.sendNoSubscriberEvent = send;
            return this;
        }

        /**
         * Configures whether EventFlow should operate in strict mode. In strict mode, certain conditions
         * that would otherwise be logged as warnings (e.g., registering a null subscriber, invalid subscriber methods)
         * will instead throw exceptions. Default is false.
         *
         * @param strict True to enable strict mode, false otherwise.
         * @return This Builder instance for chaining.
         */
        public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }

        /**
         * Builds and returns an {@link EventFlow} instance with the configured settings.
         *
         * @return A new {@link EventFlow} instance.
         */
        public EventFlow build() {
            return new EventFlow(this);
        }
    }
}
