# Native Android Break Timer

This folder contains the native Android version of the UPS Break Timer.

## What it does

- Runs a break timer as a foreground service
- Posts a running timer notification
- Sends a warning notification before the break ends
- Sends a final lock-screen notification when break is over
- Uses Android exact alarms so it can still alert when the phone is asleep

## Important note

The GitHub connector blocked the exact upload of `app/build.gradle`, so I uploaded it as:

`app/build.gradle.txt`

Before building in Android Studio, rename:

`native-android/app/build.gradle.txt`

to:

`native-android/app/build.gradle`

Then open the `native-android` folder in Android Studio and build the APK.

## Phone setup

After installing:

1. Allow notifications.
2. Tap Exact Alarm Permission in the app and allow it.
3. If your phone has aggressive battery saving, disable battery optimization for this app.
