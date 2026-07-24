# Contributing to MeshNet

Thank you for your interest in contributing to MeshNet! We are building a resilient, offline-first mesh networking framework. 

## 1. Getting Started
1. Fork the repository.
2. Ensure you can build the project using Android Studio and Java 17.
3. Check the `ROADMAP.md` for our current priorities.

## 2. Pull Request Process
- Ensure all tests pass (`./gradlew test`).
- Ensure static analysis passes (`./gradlew detekt`).
- Update documentation if you are adding new API surfaces.
- We require PRs to be reviewed by at least one core maintainer.

## 3. Architecture & Code Style
- We strictly adhere to a **Unidirectional Data Flow (UDF)**. No business logic in Compose UI.
- All networking code MUST sit in the `core:mesh` or `core:protocol` modules, heavily utilizing Coroutines.
- We format our code using Detekt. Please ensure you do not disable Detekt rules without strong justification.
