# Mailpit Module Guide

This module owns the dogfood/local-only Mailpit mail catcher.

## Scope

- `main.tf`: Mailpit image and container, SMTP on the Docker network, web/API inbox bound to loopback only.
- `variables.tf`: container name, image, network, and loopback web/API port.
- `outputs.tf`: container name and internal/loopback endpoints consumed by operators.

Mailpit is for dogfood and local verification only. Do not route production mail through this module or expose the inbox publicly.
