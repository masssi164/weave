# MCP Module Guide

This module owns the internal Spring AI stateful Streamable HTTP runtime used by governed Weaver clients.

## Files

- `main.tf`: MCP image, OIDC/backend environment, loopback smoke port, healthcheck, and Docker network binding.
- `variables.tf`: image, ports, backend authority, and OIDC validation inputs.
- `outputs.tf`: exported MCP container identifier.

The MCP service is not a second backend. It validates OIDC at the protocol edge and delegates policy, approval, audit, and canonical domain execution to `weave-backend`.
