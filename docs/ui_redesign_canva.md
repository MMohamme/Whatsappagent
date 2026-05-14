# WA Agent Pro Control Center Redesign

Canva blueprint:

- Editable design: https://www.canva.com/d/eFJy3NZWvwmnJQe
- View design: https://www.canva.com/d/3xiWbhrTKiD6zlH
- Canva design id: `DAHJj94e05M`
- 2026-05-13 scalable UI draft prepared in Canva transaction `2793860767077113002`.
  The draft updates the board toward restored modules, component library, and operational-intelligence cues.
  Canva requires explicit user approval before the draft can be committed.
- Architecture diagrams source of truth: `docs/architecture_diagrams.md`.
  The current Canva board is a responsive single-page design-system board; the available Canva edit tools can update text but cannot add the requested new diagram pages to that existing board in this session.

Implementation direction:

- Phone-first Android control center.
- Bottom navigation for Dashboard, Review Queue, Contacts, Events, Logs.
- Top safety strip keeps Backend, Contacts, Accessibility, Auto-send pause, and Accessibility fallback visible.
- Compact operator UI with 8dp cards, semantic status colors, Material icons, dense rows, and no decorative hero layout.
- Backend, Room, and ViewModel contracts remain unchanged.

Scalable Compose implementation:

- `MainActivity.kt` is the app shell only: lifecycle, permissions, service startup, ViewModel wiring, navigation, and Retrofit setup.
- `ui/pro/ProTokens.kt` owns the reusable screen enum, color tokens, status colors, compact status labels, and date/text formatting helpers.
- `ui/pro/ProComponents.kt` owns shared shell and primitives: top safety strip, bottom navigation, section headers, metric tiles, cards, status chips, empty/loading panels, logs, lifecycle bar, and action buttons.
- `ui/pro/ProScreens.kt` owns the phone-first screen bodies and forms for Dashboard, Review Queue, Contacts, Notes, Events, Logs, Review Draft, Contact Add/Edit, and Event Create.

Operational intelligence cues:

- Dashboard surfaces review pressure, failed sends, safety state, lifecycle summary, and the newest risk signal.
- Review Queue rows show priority/next-action cues, risk chips, reason preview, and tap-through edit form.
- Contacts show trust/category, phone, active state, and a detail workflow with scoped notes.
- Events show lifecycle state, target, recipients, schedule, and direct prepare/approve/cancel affordances.
- Logs remain dense and filterable, with failure reasons kept visible in row previews.

Canva diagram target:

- Backend Database ERM
- Android Room ERM
- Gesamt-App Context
- Backend Context
- End-to-End Message Flow
- Review Queue State Flow
- Event Ticket Flow
- Contact and Notes Flow
- UI Navigation and Screen Flow
- Android Worker and Service Flow
- API Surface Map
