# Tapcode

A clearer interface for USSD on Android — starting with MTN Nigeria's `*123#`.

USSD works on every phone and needs no internet, but menus arrive as numbered
text and sessions time out while you read. Tapcode keeps USSD as the transport
and replaces the interface: you tap buttons in a normal Android screen, and the
app drives the USSD dialog for you behind a full-screen overlay.

> **Not affiliated with MTN Nigeria.** Tapcode is an independent client "for
> MTN lines". No MTN branding is used or endorsed.

## Status

Release 1 (MVP) development. P0 scope implemented as a walking skeleton:

| Area | Delivered |
|---|---|
| Session engine | Dial on chosen SIM (SE-01), single-response quick answers via `sendUssdRequest` (SE-02), dialog detection & reply (SE-03/04), clean cancel (SE-05), session-gated automation (SE-07), error mapping (SE-08) |
| Parsing | Numbered menu split (MP-01), bundled default config + signed remote config (MP-02), raw-text fallback (MP-03) |
| Experience | SIM home screen with quick-answer tiles (UX-01), full-screen overlay (UX-02), charge confirmation (UX-03), status screen with fix actions (UX-08) |

P1 (timeout recovery replay, shortcuts, pagination) and P2 are tracked for
later releases. Every code and path must be verified on live SIMs before
shipping (see `tapcode-prd.md` section 9).

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
```

Requires JDK 17 and an Android SDK (API 34).

## Privacy

All USSD text, replies, phone numbers and balances stay on the device. The
accessibility service reads only the carrier's USSD dialog and only while a
Tapcode session is active. Analytics record events, never screen text.
See `tapcode-prd.md` section 11.

## License

MIT