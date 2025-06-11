# EventFlow macOS Support (Placeholder)

This `eventflow-mac` submodule is currently a **placeholder** for any potential future macOS-specific functionalities or integrations within the EventFlow library. This could include support for specific macOS UI toolkits (like AppKit/Cocoa if accessed via Java bindings) or other operating system services.

At present, there are no macOS-specific components in EventFlow.

## General JVM Applications on macOS

For general Java applications running on macOS (including server-side applications or desktop applications):

*   **`eventflow-core`**: This module contains all the fundamental EventFlow functionalities and is platform-agnostic. It can be used directly for most JVM applications on macOS.
*   **`eventflow-jvm`**: If your desktop application uses Swing (which runs on macOS), this module provides `SwingMainThreadSupport` for proper UI thread management on the Event Dispatch Thread (EDT).

If you are developing a Java application using macOS-specific UI frameworks (e.g., via Java Native Interface - JNI, or libraries like SWT that might have specific macOS threading considerations not covered by Swing's EDT), and require specialized main thread support, please consider opening an issue or discussion in the EventFlow project repository.

There are no specific dependencies to add for `eventflow-mac` at this time. Please refer to the README files in the `core` or `jvm` submodules for their respective setup instructions if applicable to your project.
