# Implementation plan: Governed Weaver runtime and tool approval contract

**Spec**: `specs/0007-governed-weaver-runtime/spec.md`
**Branch**: `feat/s8s9-weaver-governed-runtime`
**Issues**: #433, #446, #447, #448, #449

## Summary

This slice turns the existing disabled-by-default Weaver runtime placeholder into an implementation-ready security contract. It does not start containers or publish an image. It proves the policy-generated runtime profile, domain tool registry, approval boundary, audit, and support-safe evidence requirements.

## Constitution check

- Product-first/provider-neutral boundary preserved: yes.
- Weaver remains governed and disabled by default: yes.
- Runtime/model/tool providers separated: yes.
- Raw provider tokens excluded: yes.
- Accessibility, supportability, auditability, deployability treated as release blockers: yes.

## PR train position

This PR can land after the domain registry foundation because it consumes provider-neutral capabilities and does not modify parallel domain registry files. Follow-up PRs may add Admin Console UX for member opt-in and approval screens.

## Final gates

- `./gradlew specContract`
- `./gradlew acceptanceContract`
- `./gradlew serverCi`
- `./gradlew docsCheck`
