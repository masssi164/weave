# Persistent Dogfood Member

The protected dogfood path maintains one human client-testing member without Admin Console access, manual Keycloak editing, or an initial password. It is separate from the disposable CI `test` account and has only the `member` role plus the configured product capability groups.

The member is Keycloak runtime data, not an OpenTofu user resource. The immutable subject reference is stored on the dedicated runner outside the Git checkout so routine checkout cleanup and deployment cannot erase the persistence invariant.

## Protected GitHub workflow

Run **Dogfood Member** from GitHub Actions on a phone or desktop. The workflow uses only protected `dogfood` environment configuration and offers:

- `status`: report `missing`, `pending`, or `active` with support-safe hashes;
- `ensure`: create and mail an absent member once; pending and active members are unchanged;
- `resend-activation`: resend only for a pending member; an active member returns `account_already_active`.

The workflow does not accept arbitrary usernames or email addresses, cannot grant owner/admin authority, and never writes an activation URL to logs or artifacts. Open the resulting message from Safari at `https://mail.weave.test:44443` on the allowed private LAN.

## Runner helper

The workflow invokes:

```bash
cd infra/weave-workspace
./dogfood-member.sh status
./dogfood-member.sh ensure
./dogfood-member.sh resend-activation
```

Required protected configuration:

- environment variable `WEAVE_DOGFOOD_MEMBER_USERNAME`;
- environment secret `WEAVE_DOGFOOD_MEMBER_EMAIL`;
- environment variable `WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME`;
- optional environment variable `WEAVE_DOGFOOD_MEMBER_GROUPS`.

The helper also loads the generated Keycloak bootstrap environment on the dedicated runner. Active-member verification requires the same immutable subject, Keycloak organization membership, `weave-app` `member` role, and expected groups. A missing or changed recorded subject fails closed rather than creating a replacement.

Password and passkey recovery for an active account stays in Keycloak. It is never implemented as another invitation.

## Deployment behavior

Ordinary dogfood deployment checks the member before and after apply and runs a second OpenTofu plan for both infrastructure stages. It preserves persistent volumes, contains no reset input or pre-authorized destructive confirmation, and fails when the subject changes or the second plan contains drift. Identity-data reset remains a separate explicitly approved backup/restore operation.
