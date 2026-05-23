# Introduce a backend API boundary in the Flutter client

## Problem

The backend is meant to slim the Flutter app, but the app currently has no explicit integration boundary for backend-owned APIs. Without that boundary, backend work will either stay unused or drift into replacing direct Matrix/Nextcloud auth flows that should remain native.

## Proposal

- Add a dedicated backend integration layer in Flutter, parallel to the existing feature/integration layout
- Start with a typed client for backend JWT-authenticated REST calls
- Keep Matrix Native OAuth 2.0 and Nextcloud Login Flow v2/app-password handling inside the client
- Route only backend-owned workflows through the new API layer

## Acceptance criteria

- `weave` has a clear backend integration package with typed request/response models
- access tokens from the app can be attached to backend API calls without coupling UI code to HTTP details
- direct Matrix and Nextcloud user-session flows remain in their current client-owned boundaries
- the first backend endpoint consumed by Flutter is a simple contract endpoint such as `/api/me` or workspace capabilities
