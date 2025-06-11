# EventFlow Android Support

The `eventflow-android` submodule provides Android-specific enhancements for the EventFlow library, primarily focusing on main thread support crucial for UI operations.

## `AndroidMainThreadSupport`

Android applications have a main UI thread, and all UI updates must happen on this thread. EventFlow uses the `MainThreadSupport` interface to abstract how tasks are posted to the main thread. The `eventflow-android` module provides a concrete implementation: `com.codirex.eventflow.android.AndroidMainThreadSupport`.

This class uses Android's `Looper` and `Handler` to ensure that subscriber methods annotated with `@Subscribe(threadMode = ThreadMode.MAIN)` are executed on the Android main UI thread.

**Importance for UI Updates:**
If a subscriber method needs to interact with the UI (e.g., update a `TextView`, show a `Toast`, refresh a `RecyclerView`), it *must* run on the main thread. Using `ThreadMode.MAIN` with `AndroidMainThreadSupport` configured ensures this, preventing common Android errors like `CalledFromWrongThreadException`.

## Initializing EventFlow for Android

To use `ThreadMode.MAIN` effectively in an Android application, you need to configure your `EventFlow` instance with `AndroidMainThreadSupport`.

You can do this when building a custom `EventFlow` instance:

```java
import com.codirex.eventflow.EventFlow;
import com.codirex.eventflow.android.AndroidMainThreadSupport;

// ...

EventFlow eventFlow = new EventFlow.Builder()
                            .mainThreadSupport(new AndroidMainThreadSupport())
                            .build();
```

### Configuring the Default Instance (Recommended for Android)

For convenience, especially in Android applications where a single, consistently configured EventFlow instance is often desired, you can configure the default `EventFlow` instance. This is typically done in your custom `Application` class.

**Example: `MyApplication.java`**

```java
package com.example.myapp;

import android.app.Application;
import com.codirex.eventflow.EventFlow;
import com.codirex.eventflow.android.AndroidMainThreadSupport;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Configure the default EventFlow instance for Android
        // This ensures EventFlow.getDefault() is ready for ThreadMode.MAIN
        if (EventFlow.getDefault() == null) { // Check if already configured (e.g., by a library)
            EventFlow.Builder builder = new EventFlow.Builder()
                    .mainThreadSupport(new AndroidMainThreadSupport())
                    .logSubscriberExceptions(true); // Recommended for debugging

            // In debug builds, you might want stricter settings
            if (BuildConfig.DEBUG) {
                builder.logNoSubscriberMessages(true)
                       .sendNoSubscriberEvent(true)
                       .strictMode(true);
            }

            EventFlow.setDefault(builder.build());
        }
    }
}
```
*Remember to register your `MyApplication` class in your `AndroidManifest.xml`:*
```xml
<application
    android:name=".MyApplication"
    ...>
    ...
</application>
```

Once the default instance is configured, calls to `EventFlow.getDefault()` throughout your app will return this Android-ready instance.

## Android-Specific Considerations

*   **Lifecycle Management**: Be mindful of Android component lifecycles (`Activity`, `Fragment`, `Service`). Register subscribers in appropriate lifecycle methods (e.g., `onStart()`, `onResume()`) and unregister them in corresponding teardown methods (e.g., `onStop()`, `onPause()`) to prevent memory leaks and unwanted event handling.
    ```java
    // In an Activity or Fragment
    @Override
    protected void onStart() {
        super.onStart();
        EventFlow.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventFlow.getDefault().unregister(this);
        super.onStop();
    }
    ```
*   **Performance**: While EventFlow is optimized, avoid performing very long-running tasks directly in subscriber methods, even in `ThreadMode.BACKGROUND` if it's shared. For extensive background work, consider using `ThreadMode.ASYNC` or dedicated Android concurrency utilities like `AsyncTask`, `Coroutines` (Kotlin), or `ExecutorService`.
*   **ProGuard/R8**: If you are using ProGuard or R8 for code shrinking and obfuscation, ensure that your subscriber methods are not removed or renamed if they are found via reflection. Using the `eventflow-processor` can help mitigate this by generating an index, thus avoiding reflection for indexed subscribers. If not using the processor, you might need to add ProGuard rules:
    ```proguard
    -keepclassmembers class ** {
        @com.codirex.eventflow.annotation.Subscribe <methods>;
    }
    -keepclassmembers class * extends com.codirex.eventflow.api.EventFlowIndex
    ```
    (The exact rules might vary based on your setup. Using the annotation processor is generally recommended.)

## Dependency

To use `eventflow-android`, add the following dependency to your app's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.codirex.eventflow:eventflow-core:LATEST_VERSION' // Core is always needed
    implementation 'com.codirex.eventflow:eventflow-android:LATEST_VERSION'
    // ... other dependencies
}
```
*(Replace `LATEST_VERSION` with the actual latest version number.)*

By integrating `eventflow-android`, you ensure seamless and correct execution of event subscribers on the Android main UI thread, making UI updates from background operations safe and straightforward.
