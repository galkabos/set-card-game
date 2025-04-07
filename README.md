# Set Card Game - Concurrent Java Implementation

## Overview

This project is a concurrent Java implementation of a simplified version of the **Set card game**, developed as part of a university assignment for practicing **Java Concurrency, Synchronization**, and **Unit Testing**.

The project includes a complete implementation of the game logic while utilizing multithreading for simulating both **human** and **AI players**, managed by a central **Dealer** thread.

---

## Game Description

- Each card has **4 features**, each with **3 possible values** (e.g., color, number, shape, shading).
- A **legal set** of 3 cards has the property that **each feature is either the same across all cards or different**.
- Players race to find valid sets on a 3x4 grid of cards placed on the table.
- Once a set is submitted:
  - If correct → the player earns a point and gets a short freeze.
  - If incorrect → the player is penalized with a longer freeze.

---

## Components

### 🃏 Table.java
- Represents the shared game board and token state.
- Uses `Vector<Vector<Integer>>` to store token placements, providing thread-safe operations.
- All interactions with the table (placing/removing cards and tokens) are synchronized by design using thread-safe collections and controlled access.


### 👥 Player.java
- Each player runs in its own thread (either human-controlled or AI).
- AI players have an additional internal thread that simulates random key presses.
- Maintains a queue of player actions (up to 3 per set).
- Communicates with the dealer via a synchronized blocking queue to validate sets.
- Uses `volatile` flags for proper visibility of shared state (e.g., `terminate`).
- Simulates penalties and points using `Thread.sleep()` with UI feedback.


### 🤵 Dealer.java
- Controls the flow of the game in a single thread.
- Starts and manages player threads.
- Deals and removes cards from the table.
- Maintains a FIFO `BlockingQueue` of player set requests.
- Checks sets, applies rewards or penalties, and tracks countdown timers.
- Gracefully terminates all threads in reverse order.


---

## Features

- ✅ Thread-safe implementation using `BlockingQueue`, synchronized methods, and shared data protection.
- ✅ Human and AI player support.
- ✅ Countdown timer with visual UI updates.
- ✅ Fair set validation (FIFO queue for requests).
- ✅ Configurable settings via `config.properties`.


