# EventFlow Annotation Processor

The `eventflow-processor` submodule is an annotation processor for the EventFlow library. Its primary purpose is to improve the performance of event dispatching by eliminating the need for reflection at runtime to find subscriber methods.

## How It Works

When you use EventFlow, you typically annotate methods with `@Subscribe` to mark them as event subscribers. Without an index, EventFlow needs to use Java reflection at runtime to scan registered objects, find these annotated methods, and gather information about them (like the event type, thread mode, priority, etc.). Reflection can be relatively slow, especially on resource-constrained environments like Android or in applications with many subscribers.

The `eventflow-processor` runs during the compilation phase of your project. It scans your codebase for classes containing `@Subscribe` methods. For each such class, it gathers all necessary metadata about its subscriber methods and generates a Java class called an "EventFlow Index" (specifically, a class named `MyEventFlowIndex` in a defined package, e.g., `com.example.EventFlow.generated`).

This generated index class contains a pre-compiled mapping of subscriber classes to their subscriber methods and all their properties. When the `eventflow-core` library initializes, it checks for the presence of this generated index. If found, EventFlow uses the index to directly look up subscriber information, completely bypassing the need for runtime reflection for those indexed classes.

**Benefits:**
*   **Performance Boost:** Significantly faster subscriber registration and event dispatch, as direct lookups are much faster than reflection.
*   **Reduced Startup Time:** Less reflection during initialization can lead to quicker application startup.
*   **ProGuard/R8 Friendly:** Since subscriber methods are explicitly referenced in the generated index, they are less likely to be accidentally removed or renamed by code shrinking tools like ProGuard or R8, potentially reducing the need for complex keep rules for subscribers.

## Usage

For most users, the annotation processor is a "setup-once-and-forget" component. Once configured in your build system, it works automatically during compilation.

### Configuration

You need to add `eventflow-processor` as an annotation processor dependency in your build configuration.

#### Gradle (Java or Android projects)

Add the following to your module's `build.gradle` file:

```gradle
dependencies {
    // EventFlow core library (required)
    implementation 'com.codirex.eventflow:eventflow-core:LATEST_VERSION'

    // EventFlow Annotation Processor
    annotationProcessor 'com.codirex.eventflow:eventflow-processor:LATEST_VERSION'

    // If you are using platform-specific modules, include them as well:
    // implementation 'com.codirex.eventflow:eventflow-android:LATEST_VERSION'
    // implementation 'com.codirex.eventflow:eventflow-jvm:LATEST_VERSION'
}
```
*(Replace `LATEST_VERSION` with the actual latest version number for all EventFlow modules, ensuring they are consistent.)*

**Note for Kotlin projects using KAPT:**
If you are using Kotlin, you would use `kapt` instead of `annotationProcessor`:
```gradle
// In your build.gradle (Module :app)
// apply plugin: 'kotlin-kapt' // Ensure KAPT plugin is applied at the top

dependencies {
    // ... other dependencies
    implementation 'com.codirex.eventflow:eventflow-core:LATEST_VERSION'
    kapt 'com.codirex.eventflow:eventflow-processor:LATEST_VERSION'
}
```

#### Maven

Add the following to your `pom.xml` within the `<dependencies>` section and configure the `maven-compiler-plugin` to use the annotation processor:

```xml
<dependencies>
    <dependency>
        <groupId>com.codirex.eventflow</groupId>
        <artifactId>eventflow-core</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
    <dependency>
        <groupId>com.codirex.eventflow</groupId>
        <artifactId>eventflow-processor</artifactId>
        <version>LATEST_VERSION</version>
        <scope>provided</scope> <!-- Annotation processors are not needed at runtime -->
    </dependency>
    <!-- ... other dependencies -->
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version> <!-- Use a recent version -->
            <configuration>
                <source>1.8</source> <!-- Or your Java version -->
                <target>1.8</target> <!-- Or your Java version -->
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.codirex.eventflow</groupId>
                        <artifactId>eventflow-processor</artifactId>
                        <version>LATEST_VERSION</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
*(Replace `LATEST_VERSION` with the actual latest version number.)*

After configuring the processor, rebuild your project. EventFlow will automatically detect and use the generated index if available. While this processor is optional, it is **highly recommended** for production builds and larger applications due to the significant performance benefits, especially on Android.
