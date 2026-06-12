# MotoGP Fantasy

MotoGP Fantasy is a native Android app for creating a fantasy MotoGP team, following the race calendar, and comparing scores in leagues. The app was built with Kotlin and Jetpack Compose, while Firebase is used for authentication and storing app data.

The main idea is simple: a user signs in with Google, selects riders and a constructor within a budget, joins or creates leagues, and follows race results through the season.

## Features

- Google sign-in with Firebase Authentication
- Fantasy team builder with rider and constructor selection
- Budget tracking while building a team
- Public and private leagues
- League joining with invite codes
- Leaderboards and fantasy scoring
- MotoGP race calendar
- Race results overview
- User profile screen
- Local race notifications

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Hilt
- Firebase Authentication
- Firebase Firestore
- Retrofit and OkHttp
- Gradle Kotlin DSL

## Project Structure

The Android project is inside the MotoGPFantasy folder.

MotoGPFantasy/
  app/                 Android application module
  gradle/              Gradle wrapper files
  apk/                 Built debug APK for testing/installing

Most of the source code is organized under:

MotoGPFantasy/app/src/main/java/com/motogp/fantasy

The main parts are split into data, repository, di, service, and ui packages.

## Running the App

Open the MotoGPFantasy folder in Android Studio and run the app configuration.

The project is intended to be run as a debug build. If Gradle asks for a JDK, use the Android Studio bundled JDK or JDK 17.

To build from terminal on Windows:

cd MotoGPFantasy
.\gradlew.bat :app:assembleDebug

The generated APK is located at:

MotoGPFantasy/app/build/outputs/apk/debug/app-debug.apk

A copy of the APK is also included here:

MotoGPFantasy/apk/app-debug.apk

## Notes

Google Sign-In depends on the SHA-1 certificate configured in Firebase. The included APK is built with the current debug configuration. If the project is rebuilt on another computer, that computer's debug SHA-1 may need to be added in Firebase for Google Sign-In to work.
