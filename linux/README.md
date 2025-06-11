# EventFlow Linux Support (Placeholder)

This `eventflow-linux` submodule is currently a **placeholder** for any potential future Linux-specific functionalities or integrations within the EventFlow library.

At present, there are no Linux-specific components in EventFlow.

## General JVM Applications on Linux

For general Java applications running on Linux (including server-side applications or desktop applications not using a specific UI toolkit covered by other modules):

*   **`eventflow-core`**: This module contains all the fundamental EventFlow functionalities and is platform-agnostic. It can be used directly for most JVM applications on Linux.
*   **`eventflow-jvm`**: If your desktop application uses Swing, this module provides `SwingMainThreadSupport` for proper UI thread management, which works on Linux systems where Swing is used.

If you have a need for Linux-specific main thread support (e.g., for a UI toolkit other than Swing that has specific threading requirements on Linux not covered by standard JVM approaches), please consider opening an issue or discussion in the EventFlow project repository.

There are no specific dependencies to add for `eventflow-linux` at this time. Please refer to the README files in the `core` or `jvm` submodules for their respective setup instructions if applicable to your project.
