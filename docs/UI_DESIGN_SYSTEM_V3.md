# Uzzap Android UI Design System v3.0 — Claymorphism

**Status: FINAL DESIGN BASELINE**

The Uzzap Android app uses **Claymorphism v3.0** as its canonical visual design. The previous v2.0 Modern Classic presentation is retained only as historical reference; new UI work must follow v3 unless a screen-specific accessibility requirement requires otherwise.

## Visual direction

- Soft, friendly, modern mobile UI
- Light lavender/blue canvas with white elevated surfaces
- Uzzap green used for primary actions, online state and positive confirmation
- Purple/indigo used for navigation and brand identity
- Cyan used as a secondary gradient endpoint/accent
- Rounded cards and controls with restrained depth
- Subtle shadows instead of heavy borders
- Compact information hierarchy suitable for chat-heavy screens
- No retro-black default chrome

## Core tokens

| Token | Value | Use |
|---|---|---|
| `uzzap_clay_bg` | `#F2F6FF` | App canvas |
| `uzzap_clay_surface` | `#FFFFFF` | Cards, dialogs, sheets |
| `uzzap_clay_primary` | `#7478F2` | Header/navigation |
| `uzzap_clay_primary_dark` | `#5559C9` | Pressed/deep brand state |
| `uzzap_clay_accent` | `#8FEA4F` | Primary CTA, online state |
| `uzzap_clay_cyan` | `#59D9D2` | Secondary gradient endpoint |
| `uzzap_clay_lilac` | `#C7B9FF` | Decorative/secondary surfaces |
| `uzzap_clay_text` | `#1E2440` | Primary text |
| `uzzap_clay_muted` | `#68708D` | Secondary text |
| `uzzap_clay_divider` | `#DCE3F4` | Hairlines and separators |

## Component rules

### App chrome
- Use a soft purple-to-cyan header treatment where a gradient is supported by the component.
- Keep title text white and concise.
- Preserve the existing online/status indicator behavior.
- Bottom navigation/softkeys use the same v3 palette and remain readable against the light canvas.

### Cards
- White surface on the v3 canvas.
- Rounded corners; avoid sharp rectangular legacy boxes.
- Use subtle elevation/shadow for separation.
- Prefer spacing and elevation over visible outlines.

### Buttons
- Primary action: green rounded button.
- Secondary action: white/light surface button with v3 border/shadow.
- Destructive action: soft red state; do not use legacy black/red outlined controls as the default.

### Chat
- Preserve all existing Uzzap message color codes and emoticon behavior.
- User messages and incoming messages should remain visually distinct through surface tint/placement, not excessive borders.
- Composer follows the rounded v3 control language.

### Status colors
- Online: `#7ED957`
- Warning: `#F2B84B`
- Error: `#E56B6F`

## Screen coverage

The v3 visual language applies consistently to:

1. Login / Splash
2. Welcome
3. Uzzap Menu
4. Communication Center
5. Chat Rooms
6. Room Chat
7. Buddy List
8. Private Chat
9. Profile
10. Moderator Console
11. Moderation Audit
12. Room Invitations
13. Delivery & Read Receipts
14. Diagnostics
15. About Uzzap
16. Settings

## Implementation rule

`ThemeManager` defaults to the `clay` theme. The legacy `classic` preference is mapped to `UzzapTheme.Clay` so existing installations migrate visually without losing their saved preference. Dark/black/blue/green variants remain supported as palette alternatives, but their component geometry and interaction patterns should follow the v3 system.

## Acceptance criteria for future UI changes

- New screens use v3 semantic colors instead of introducing ad-hoc colors.
- New cards/buttons/dialogs follow the rounded/elevated v3 component language.
- Existing messaging, moderation, realtime, notification and diagnostics behavior is not changed merely for visual work.
- Accessibility contrast and touch-target requirements remain higher priority than decorative styling.
- Any new asset must support the v3 light canvas and the app's theme-aware asset strategy.


## NameThatUI-informed component vocabulary

The v3 implementation uses the applicable mobile interaction patterns documented by NameThatUI: bottom navigation/tab bar, selected tabs, badges, presence indicators, chat bubbles, cards, headers/navigation bars, form fields, alerts/callouts, modal/sheet surfaces, popovers, empty states, accordion/disclosure groups, search fields, incremental history loading, progress/loading states, dividers, fixed navigation/composer chrome, focus states, truncation and semantic status labels.

Desktop/macOS-only patterns are not copied literally. The implementation uses Android-native equivalents while preserving Uzzap's interaction behavior.

See docs/UI_NAMETHATUI_MAPPING.md for the full pattern map and acceptance checklist.
