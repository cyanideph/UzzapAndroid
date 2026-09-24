# Uzzap Android — NameThatUI-informed UI redesign map

**Target:** Uzzap Android UI Design System v3 / Claymorphism
**Reference:** NameThatUI visual dictionary, checked September 25, 2026.
**Status:** Implementation guide for PR #59.

NameThatUI provides standard names, anatomy and implementation vocabulary for common interface patterns. Uzzap adopts the applicable mobile patterns rather than copying the site's visual design.

## Applied to Uzzap

| Pattern | Uzzap implementation | Priority |
|---|---|---|
| Bottom Navigation / Tab Bar | Buddy, Rooms and joined-room tabs in the fixed navigation strip | Core |
| Selected Tab | Accent-tinted rounded selected state | Core |
| Count Badge | Unread buddy/DM badge | Core |
| Status Dot / Presence Indicator | Existing online/offline assets plus semantic online color | Core |
| Chat Bubble | Private-message bubbles and room message cards | Core |
| Avatar / Identity | Circular private-chat identity treatment | Core |
| Card | Room, buddy-group, dialog and empty-state surfaces | Core |
| Header / Navigation Bar | Gradient Uzzap title bar | Core |
| Hamburger / Overflow Menu | Existing options menus retain behavior; visual treatment follows v3 dialogs/popups | Core |
| Form Field | Rounded inputs with helper/error states | Core |
| Alert / Callout / Banner | System room messages and connection/error states use soft surfaces | Core |
| Modal / Sheet / Scrim | UzzapDialogs shared dialog surface | Core |
| Popover / Dropdown Menu | Existing J2me popup actions remain behavior-compatible | Core |
| Tooltip | Accessibility/content descriptions for icon-only actions; use only where useful | Medium |
| Skeleton / Loading | Loading states should preserve layout shape rather than jump between screens | Medium |
| Empty State | Buddy list and private chat empty states | Core |
| Tabs | Horizontal room/navigation tabs where a shared content region is appropriate | Core |
| Badge / Chip / Pill / Tag | Unread counts, status labels and compact state labels | Core |
| Accordion / Disclosure | Buddy groups expand/collapse | Core |
| Toggle Group / Segmented Control | Settings and mode selectors where only one persistent choice exists | Medium |
| Switch / Checkbox / Radio | Settings and moderation forms according to selection semantics | Medium |
| Search Field | Room and buddy search | Core |
| Combobox / Typeahead | Search suggestions when server-backed search is introduced | Future |
| Pagination / Incremental loading | Existing room/private-chat history loading | Core |
| Progress / Spinner | Network/loading states | Core |
| Divider / Separator | Use sparingly; prefer spacing/elevation on cards | Core |
| Sticky / Fixed Positioning | Title/navigation/composer chrome | Core |
| Focus Ring | Keyboard/TV/accessibility focus states | Medium |
| Truncation / Line Clamp | Room names, titles and long usernames | Core |
| Spring / Easing | Optional motion for expandable groups and pressed states | Medium |
| Timeline | Moderation audit/history can use a vertical event timeline | Future |
| Steps | Multi-stage account/setup flows when applicable | Future |
| Avatar Group | Moderator/buddy summaries when multiple people are represented | Future |
| Carousel / Lightbox | Image/profile media only if media features are expanded | Future |

## Not copied literally

NameThatUI also catalogs macOS-specific controls and desktop/web patterns. Those are not blindly added to Android. Examples include macOS menu bars, traffic lights, Finder sidebars, resize handles, command palettes, hover cards, desktop pointer states and AppKit-specific controls.

The rule is **semantic equivalence, not visual imitation**: use the Android-native interaction that represents the same intent.

## Uzzap v3 visual rules

- Canvas: uzzap_clay_bg
- Surface: uzzap_clay_surface
- Brand: purple/indigo with cyan gradient endpoint
- Primary action: Uzzap green accent
- Text: uzzap_clay_text
- Muted text: uzzap_clay_muted
- Online: uzzap_clay_online
- Warning: uzzap_clay_warning
- Error: uzzap_clay_error
- Rounded surfaces and controls
- Subtle elevation instead of heavy outlines
- Minimum touch target: 44dp for primary/icon navigation controls
- Preserve Uzzap color-code markup, emoticons, message semantics and backend behavior
- No functional/backend changes solely for visual redesign

## Current PR implementation

PR #59 now includes:

1. Semantic v3 palette and runtime theme baseline.
2. Gradient title/header chrome.
3. Light elevated navigation and softkey surfaces.
4. Rounded room/category cards.
5. Rounded room composer and primary Send control.
6. Rounded buddy/profile group surfaces.
7. Presence and unread-state presentation.
8. Room message callout/bubble treatment.
9. Private-message bubble theming.
10. Shared dialog theming through ThemeManager.
11. Horizontal scrolling tab strip for joined rooms.
12. Explicit NameThatUI-to-Uzzap component mapping.

## E2E visual acceptance checklist

- Login/welcome remains readable on light v3 canvas.
- Room browsing cards have consistent spacing, radius and touch targets.
- Room chat composer stays above the navigation/IME area.
- Room messages retain color codes and emoticons.
- Private chat bubbles preserve sender/recipient distinction.
- Presence status remains visible and semantically correct.
- Unread badges remain visible without dominating the UI.
- Dialogs remain readable in every supported palette.
- Existing Realtime, Supabase, notification and moderation behavior is unchanged.
- Android back navigation and existing softkey actions continue to work.