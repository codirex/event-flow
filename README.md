# EventFlow

EventFlow is a lightweight, annotation-based event bus library for Java and Android applications. It facilitates decoupled communication between different components of an application, leading to more modular, maintainable, and scalable code.

## Purpose

The core purpose of EventFlow is to allow different parts of an application to communicate with each other without having direct dependencies. Components can publish events, and other components can subscribe to these events without knowing who is publishing them or how many other components are listening. This promotes loose coupling and simplifies the architecture of complex applications.

## Key Features

*   **Annotation-based Event Subscription**: Easily subscribe to events by annotating methods with `@Subscribe`.
    ```java
    @Subscribe
    public void onUserLoggedIn(UserLoggedInEvent event) {
        // Handle the event
    }
    ```
*   **Thread Mode Management**: Control the thread on which subscriber methods are invoked using `ThreadMode`:
    *   `POSTING`: Subscriber is called in the same thread that posted the event (default).
    *   `MAIN`: Subscriber is called in the main application thread (e.g., Android UI thread, Swing EDT). Requires platform-specific support.
    *   `BACKGROUND`: Subscriber is called in a single background thread, ensuring sequential execution.
    *   `ASYNC`: Subscriber is called in a separate thread from a thread pool, allowing for concurrent execution.
*   **Sticky Events**: Post events that are retained in memory. New subscribers for sticky events will receive the last sticky event of that type immediately upon registration.
    ```java
    eventFlow.postSticky(new ProfileUpdatedEvent(currentUserProfile));
    ```
*   **Subscriber Priorities**: Define the order in which subscribers receive events. Subscribers with higher priority values are called before those with lower priority.
    ```java
    @Subscribe(priority = 10)
    public void onHighPriorityEvent(MyEvent event) { /* ... */ }
    ```
*   **Error Handling**: Implement a custom `ErrorHandler` to manage exceptions that occur within subscriber methods or during event dispatch.
*   **Platform Support**:
    *   **Core JVM**: Usable in any Java application.
    *   **Android**: Provides `AndroidMainThreadSupport` for easy integration with the Android UI thread.
    *   **JVM (Swing)**: Provides `SwingMainThreadSupport` for integration with Swing applications.
*   **Annotation Processor (Optional)**: Includes an annotation processor (`eventflow-processor`) that can generate an index of subscriber methods at compile time. This can improve performance by avoiding reflection at runtime, which is especially beneficial on Android.

## Basic Usage

1.  **Get an EventFlow instance**:
    You can use the default singleton instance or build a custom one.

    ```java
    // Get default instance
    EventFlow eventFlow = EventFlow.getDefault();

    // Or build a custom instance
    EventFlow myEventFlow = new EventFlow.Builder()
                                .logSubscriberExceptions(true)
                                .strictMode(false)
                                .build();
    ```

2.  **Define your event class**:
    An event can be any POJO (Plain Old Java Object).

    ```java
    public class MessageEvent {
        public final String message;
        public MessageEvent(String message) {
            this.message = message;
        }
    }
    ```

3.  **Create a subscriber**:
    Register an object that contains methods annotated with `@Subscribe`.

    ```java
    public class MySubscriber {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onMessageEvent(MessageEvent event) {
            System.out.println("Message received on main thread: " + event.message);
            // Update UI or handle event
        }
    }
    ```

4.  **Register and unregister your subscriber**:

    ```java
    MySubscriber subscriber = new MySubscriber();
    eventFlow.register(subscriber);

    // ... later, when the subscriber is no longer needed
    eventFlow.unregister(subscriber);
    ```

5.  **Post an event**:

    ```java
    eventFlow.post(new MessageEvent("Hello EventFlow!"));
    ```

## Adding EventFlow to Your Project

EventFlow is modular. You'll typically include `eventflow-core` and then add platform-specific modules or the annotation processor as needed.

### Gradle

Add the following dependencies to your `build.gradle` file:

```gradle
dependencies {
    // Core library (required)
    implementation 'com.codirex.eventflow:eventflow-core:LATEST_VERSION'

    // For Android projects, to use AndroidMainThreadSupport
    implementation 'com.codirex.eventflow:eventflow-android:LATEST_VERSION'

    // For Swing JVM projects, to use SwingMainThreadSupport
    implementation 'com.codirex.eventflow:eventflow-jvm:LATEST_VERSION'

    // Annotation processor (optional, for performance)
    annotationProcessor 'com.codirex.eventflow:eventflow-processor:LATEST_VERSION'
}
```
*(Replace `LATEST_VERSION` with the actual latest version number)*

## Submodules

This project is organized into several submodules:

*   [`core/`](./core/README.md) - The core EventFlow library.
*   [`android/`](./android/README.md) - Android-specific support (e.g., main thread integration).
*   [`jvm/`](./jvm/README.md) - JVM-specific support (e.g., Swing main thread integration).
*   [`processor/`](./processor/README.md) - The annotation processor for generating subscriber indexes.
*   [`examples/`](./examples/README.md) - Usage examples for different platforms.

*(Note: The `README.md` files for submodules will be created in subsequent steps.)*

## Contributing

Contributions are welcome! Please refer to the contributing guidelines (to be created) for more details.

## License

EventFlow is released under the MIT License. See the [LICENSE](./LICENSE) file for details.
