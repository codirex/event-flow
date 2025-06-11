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

    private EventFlow() {

        this(new Builder());
    }

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

        public Builder() {}

        public Builder errorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
            return this;
        }

        public Builder asyncExecutorService(ExecutorService executorService) {
            this.asyncExecutorService = executorService;
            return this;
        }

        public Builder backgroundPoster(BackgroundPoster backgroundPoster) {
            this.backgroundPoster = backgroundPoster;
            return this;
        }

        public Builder mainThreadSupport(MainThreadSupport mainThreadSupport) {
            this.mainThreadSupport = mainThreadSupport;
            return this;
        }

        public Builder logSubscriberExceptions(boolean log) {
            this.logSubscriberExceptions = log;
            return this;
        }

        public Builder logNoSubscriberMessages(boolean log) {
            this.logNoSubscriberMessages = log;
            return this;
        }

        public Builder sendSubscriberExceptionEvent(boolean send) {
            this.sendSubscriberExceptionEvent = send;
            return this;
        }

        public Builder sendNoSubscriberEvent(boolean send) {
            this.sendNoSubscriberEvent = send;
            return this;
        }

        public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }

        public EventFlow build() {
            return new EventFlow(this);
        }
    }
}
