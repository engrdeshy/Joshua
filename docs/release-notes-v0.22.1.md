# Bible Flourish 0.22.1

This maintenance release fixes a startup crash that could occur when the saved reading position loaded before the chapter list, particularly on Android 16.

- Prevents the chapter selector from expanding a book before its data is available.
- Uses Android's predictive-back-compatible dispatcher for drawers and verse details.
- Adds regression coverage for the asynchronous startup sequence.
- Keeps all Bible Flourish 0.22.0 features, including cross-references, reading reminders, Android 16 support, and privacy protections.

The APK is signed with the same Bible Flourish release key as 0.22.0, so it can be installed as an update.
