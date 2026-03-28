# Weave 🧶

A unified, accessibility-first, open-source collaboration client built with Flutter.

## 🎯 The Vision
Organizations relying on self-hosted infrastructure often face severe workflow fragmentation on mobile devices. **Weave** solves this by unifying identity, communication, and file management into a single, cohesive application.

Instead of switching between multiple apps, Weave acts as a unified frontend that weaves together three core open-source pillars:
* **Identity:** Single Sign-On via [Authentik](https://goauthentik.io/) (OIDC).
* **Communication:** Real-time, E2EE messaging via the [Matrix Protocol](https://matrix.org/).
* **Productivity:** File management (WebDAV) and Calendar (CalDAV) via [Nextcloud](https://nextcloud.com/).

### ♿ Accessibility First
Accessibility is not an afterthought in this project. Weave is built from the ground up to be fully compliant with screen readers (VoiceOver for iOS, TalkBack for Android). Custom semantic trees and logical reading orders are implemented across all complex UI elements (like chat bubbles and file trees) to ensure an inclusive user experience.

## 🏗️ Architecture
This project follows a strict **Feature-First (Clean Architecture)** approach to maintain scalability across different backend protocols.

\`\`\`text
lib/
├── core/             # App-wide configurations (Routing, Theme, Global A11y utils)
├── features/         # Isolated feature modules
│   ├── auth/         # OIDC authentication & token management
│   ├── chat/         # Matrix SDK integration & Chat UI
│   ├── files/        # WebDAV integration & File Explorer UI
│   └── calendar/     # CalDAV integration & Agenda UI
└── shared/           # Reusable UI widgets (Buttons, Dialogs)
\`\`\`

## 🚀 Getting Started

### Prerequisites
* [Flutter SDK](https://docs.flutter.dev/get-started/install) (Version 3.19.0 or higher)
* An active instance of Authentik, Matrix (e.g., Synapse), and Nextcloud.

### Installation
1. Clone the repository:
   \`git clone https://github.com/masssi164/wave.git\`
2. Install dependencies:
   \`flutter pub get\`
3. Run the application:
   \`flutter run\`

## 🤝 Contributing
Contributions are welcome! Please ensure that any UI changes include appropriate `Semantics` widgets and maintain the established clean code architecture.