# Mailpit Module Guide

This module owns the dogfood/local-only Mailpit mail catcher.

## Scope

- `main.tf`: Mailpit image, persistent SQLite volume, bounded message retention, SMTP on the Docker network, and web/API inbox bound to loopback only.
- `variables.tf`: container name, image, network, data volume, retention limit, and loopback web/API port.
- `outputs.tf`: container and volume names plus internal/loopback endpoints consumed by operators.

Mailpit is for dogfood and local verification only. Do not route production mail through this module or expose the inbox publicly.
