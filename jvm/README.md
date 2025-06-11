# EventFlow JVM Support (Swing)

The `eventflow-jvm` submodule provides JVM-specific enhancements for the EventFlow library, with a particular focus on supporting Swing applications by ensuring UI updates are correctly handled on the Event Dispatch Thread (EDT).

## `SwingMainThreadSupport`

Desktop applications built with Swing have an Event Dispatch Thread (EDT), which is the single thread responsible for handling all UI-related events and updates. Any code that modifies Swing components (e.g., updating a `JLabel`, adding an item to a `JList`, repainting a `JPanel`) *must* execute on the EDT.

EventFlow uses the `MainThreadSupport` interface to abstract how tasks are posted to an application's main thread. The `eventflow-jvm` module provides `com.codirex.eventflow.jvm.SwingMainThreadSupport`, a concrete implementation for Swing.

This class utilizes `javax.swing.SwingUtilities.invokeLater()` to ensure that subscriber methods annotated with `@Subscribe(threadMode = ThreadMode.MAIN)` are executed on the Swing EDT.

**Importance for Swing UI Updates:**
If a subscriber method needs to interact with Swing UI components, it must run on the EDT. Using `ThreadMode.MAIN` with `SwingMainThreadSupport` configured ensures this, preventing potential threading issues, visual glitches, or `IllegalStateException`s that can arise from incorrect Swing threading.

## Initializing EventFlow for Swing Applications

To use `ThreadMode.MAIN` effectively in a Swing application, you need to configure your `EventFlow` instance with `SwingMainThreadSupport`.

You can do this when building a custom `EventFlow` instance:

```java
import com.codirex.eventflow.EventFlow;
import com.codirex.eventflow.jvm.SwingMainThreadSupport;

// ...

// First, check if Swing is available, especially if your app might run in non-GUI environments
if (SwingMainThreadSupport.isSwingAvailable()) {
    EventFlow eventFlow = new EventFlow.Builder()
                                .mainThreadSupport(new SwingMainThreadSupport())
                                .build();
    // Use this eventFlow instance in your Swing application
} else {
    // Handle non-Swing environment or use a default EventFlow without MAIN thread support
    EventFlow eventFlow = EventFlow.getDefault(); // Might not support ThreadMode.MAIN correctly
}

```

### Configuring the Default Instance (Recommended for Swing)

For convenience, you can configure the default `EventFlow` instance. This is typically done at the startup of your Swing application, for example, in your `main` method.

**Example: `MainApplication.java`**

```java
package com.example.myapp;

import com.codirex.eventflow.EventFlow;
import com.codirex.eventflow.jvm.SwingMainThreadSupport;
import javax.swing.SwingUtilities;

public class MainApplication {

    public static void main(String[] args) {
        // Configure the default EventFlow instance for Swing
        if (SwingMainThreadSupport.isSwingAvailable()) {
            EventFlow.Builder builder = new EventFlow.Builder()
                    .mainThreadSupport(new SwingMainThreadSupport())
                    .logSubscriberExceptions(true); // Good for debugging

            // Optionally, more verbose logging or stricter modes for development
            // builder.logNoSubscriberMessages(true).sendNoSubscriberEvent(true);

            EventFlow.setDefault(builder.build());
        } else {
            System.err.println("Warning: Swing environment not detected. ThreadMode.MAIN might not work as expected.");
            // EventFlow.getDefault() will be a basic instance.
        }

        // Start your Swing application
        SwingUtilities.invokeLater(() -> {
            // Create and show your main JFrame, etc.
            // MyMainFrame mainFrame = new MyMainFrame();
            // mainFrame.setVisible(true);
        });
    }
}
```

Once the default instance is configured, calls to `EventFlow.getDefault()` throughout your Swing application will return this Swing-ready instance.

## Dependency

To use `eventflow-jvm`, add the following dependency to your project's build configuration.

### Gradle

Add to your `build.gradle` file:
```gradle
dependencies {
    implementation 'com.codirex.eventflow:eventflow-core:LATEST_VERSION' // Core is always needed
    implementation 'com.codirex.eventflow:eventflow-jvm:LATEST_VERSION'
    // ... other dependencies
}
```
*(Replace `LATEST_VERSION` with the actual latest version number.)*

### Maven

Add to your `pom.xml` file:
```xml
<dependencies>
    <dependency>
        <groupId>com.codirex.eventflow</groupId>
        <artifactId>eventflow-core</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
    <dependency>
        <groupId>com.codirex.eventflow</groupId>
        <artifactId>eventflow-jvm</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
    <!-- ... other dependencies -->
</dependencies>
```
*(Replace `LATEST_VERSION` with the actual latest version number.)*

By including `eventflow-jvm` and configuring `SwingMainThreadSupport`, you can reliably use `ThreadMode.MAIN` to ensure that your EventFlow subscribers interact safely with Swing UI components on the Event Dispatch Thread.
