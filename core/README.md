# EventFlow Core

The `eventflow-core` submodule provides the essential classes and functionalities for the EventFlow library. It is the foundation upon which platform-specific modules (like `eventflow-android` or `eventflow-jvm`) and the annotation processor are built.

## Main Concepts

### 1. `EventFlow` Class
This is the central class of the library. It acts as the event bus, managing event subscriptions, event posting, and the delivery of events to appropriate subscribers. You typically interact with an instance of this class to register/unregister subscribers and to post events.

### 2. Events (POJOs)
In EventFlow, an event can be any Plain Old Java Object (POJO). There's no need to extend a specific class or implement an interface for an object to be considered an event. This provides flexibility in defining your event structures.

```java
// Example of a simple event
public class UserLoggedInEvent {
    public final String userId;
    public final Date loginTime;

    public UserLoggedInEvent(String userId, Date loginTime) {
        this.userId = userId;
        this.loginTime = loginTime;
    }
}
```

### 3. `@Subscribe` Annotation
To receive events, methods in your subscriber classes must be annotated with `@Subscribe`.
A subscriber method must:
*   Be `public`.
*   Have exactly one parameter, which is the type of event it subscribes to.

```java
public class AuthLogger {
    @Subscribe
    public void onUserLoggedIn(UserLoggedInEvent event) {
        System.out.println("User " + event.userId + " logged in at " + event.loginTime);
        // Further logging actions
    }
}
```

### 4. `ThreadMode`
EventFlow allows you to control the thread on which a subscriber method is invoked. This is crucial for tasks like updating UI (which must happen on a main thread) or performing long-running operations off the main thread.

*   **`POSTING`**: (Default) The subscriber method will be called in the same thread that posted the event. This is efficient as it avoids thread switching. However, long-running subscriber methods can block the posting thread.
*   **`MAIN`**: The subscriber method will be called on the main application thread (e.g., Android UI thread, Swing EDT). If the event is posted from the main thread, the subscriber is called directly. If posted from a background thread, the event is queued for the main thread. Requires a `MainThreadSupport` implementation (e.g., from `eventflow-android` or `eventflow-jvm`).
*   **`BACKGROUND`**: The subscriber method will be called in a single background thread. Events are delivered sequentially. If the event is posted from a non-main thread, the subscriber might be called directly in that thread. If posted from the main thread, it's offloaded to the dedicated background thread.
*   **`ASYNC`**: The subscriber method will be called in a separate thread from a thread pool managed by EventFlow. This is suitable for operations that can run independently and concurrently, without blocking the posting thread or the main thread.

### 5. Sticky Events
A sticky event is an event that is retained in memory after being posted. When a new subscriber registers for a sticky event type (and its `@Subscribe` method is marked as `sticky = true`), it will immediately receive the last posted sticky event of that type, even if the event was posted before the subscriber registered. If no sticky event of that type has been posted, the subscriber will not receive anything immediately upon registration but will receive future events of that type.

Useful for representing state or on-demand data (e.g., current user status, last known location).

### 6. Subscriber Priorities
You can influence the order of event delivery to subscribers by setting a priority on the `@Subscribe` annotation. Subscribers with higher priority values will receive the event before subscribers with lower priority values. The order of delivery for subscribers with the same priority is undefined.

```java
@Subscribe(priority = 100) // High priority
public void onCriticalEvent(AppShutdownEvent event) { /* ... */ }

@Subscribe(priority = 1) // Lower priority
public void onInformationalEvent(AppShutdownEvent event) { /* ... */ }
```

### 7. Error Handling
*   **`ErrorHandler`**: You can provide a custom `ErrorHandler` to an `EventFlow` instance (via its `Builder`) to define how exceptions occurring in subscriber methods are handled. This is useful for logging or custom recovery logic.
*   **`DeadEvent`**: If an event is posted but no subscribers are found for it, EventFlow (by default) posts a `DeadEvent` wrapping the original event. You can subscribe to `DeadEvent` to catch such occurrences, which can be helpful for debugging or identifying unused events.

## Common Operations

### Getting an EventFlow Instance
```java
// Get the default singleton instance
EventFlow eventFlow = EventFlow.getDefault();

// Or, create a configured instance
EventFlow customBus = new EventFlow.Builder()
    .logSubscriberExceptions(true) // Log exceptions from subscribers
    .sendNoSubscriberEvent(true)   // Post DeadEvent if no subscribers
    .strictMode(false)             // Be lenient with subscriber validation
    .build();
```

### Registering and Unregistering Subscribers
```java
MySubscriber subscriber = new MySubscriber();

// Register
eventFlow.register(subscriber);

// Unregister (e.g., in onDestroy() of an Android Activity or when a component is disposed)
eventFlow.unregister(subscriber);
```

### Posting Events
```java
// Post a regular event
eventFlow.post(new UserLoggedInEvent("user123", new Date()));

// Post a sticky event
ConfigurationChangeEvent configEvent = new ConfigurationChangeEvent("dark_mode", true);
eventFlow.postSticky(configEvent);
```

### Defining Subscriber Methods

**Basic Subscriber:**
```java
@Subscribe
public void onSimpleEvent(SimpleEvent event) {
    // Process event in the posting thread
}
```

**Subscriber on Main Thread:**
```java
// Requires MainThreadSupport to be configured for EventFlow
@Subscribe(threadMode = ThreadMode.MAIN)
public void onUiUpdateEvent(UpdateScoreEvent event) {
    // Update UI elements here
    scoreTextView.setText(String.valueOf(event.newScore));
}
```

**Background Subscriber:**
```java
@Subscribe(threadMode = ThreadMode.BACKGROUND)
public void onDataSaveRequest(SaveProfileRequest request) {
    // Perform file I/O or database operations here
    saveProfileToDisk(request.getProfile());
}
```

**Async Subscriber:**
```java
@Subscribe(threadMode = ThreadMode.ASYNC)
public void onAnalyticsEvent(UserActionEvent event) {
    // Send analytics data to a remote server
    analyticsService.track(event);
}
```

**Subscriber with Priority:**
```java
@Subscribe(priority = 5)
public void onPaymentProcessed(PaymentEvent event) {
    // This will be called before a subscriber with priority < 5 for the same event
    updatePaymentLedger(event);
}
```

**Sticky Event Subscriber:**
```java
@Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
public void onConfigurationChanged(ConfigurationChangeEvent event) {
    // Will receive the last ConfigurationChangeEvent immediately if one was posted sticky
    // and will also receive new ones.
    applyTheme(event.isDarkModeEnabled() ? Themes.DARK : Themes.LIGHT);
}
```

### Handling Sticky Events
```java
// To get a sticky event on demand:
ConfigurationChangeEvent lastConfig = eventFlow.getStickyEvent(ConfigurationChangeEvent.class);
if (lastConfig != null) {
    // Use the last known configuration
}

// To remove a sticky event:
eventFlow.removeStickyEvent(ConfigurationChangeEvent.class);
```

The `eventflow-core` module is designed to be lean and flexible, providing the fundamental building blocks for event-driven architectures in various Java environments.
