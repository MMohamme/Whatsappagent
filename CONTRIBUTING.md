# Contributing

Thanks for your interest in WA Agent Pro Control Center. This project is an active prototype for a personal communication agent, so contributions should keep privacy, safety, and operator control in mind.

## Before You Start

- Read [README.md](README.md) for setup and architecture.
- Read [VISION.md](VISION.md) for product direction and roadmap.
- Check [docs/architecture_diagrams.md](docs/architecture_diagrams.md) before changing data flow, backend routes, workers, or send behavior.

## Development Setup

Copy local environment settings:

```powershell
Copy-Item .env.example .env
```

Run the backend from the repository root:

```powershell
python -m uvicorn backend.main:app --reload
```

Build the Android app:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Checks

Run Android compile checks:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Run Android unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Run backend tests:

```powershell
python -m pytest backend/tests
```

## Contribution Guidelines

- Keep backend, Room, and ViewModel contracts stable unless the change explicitly updates the architecture.
- Do not commit real message databases, `.env` files, logcat dumps, screenshots with private data, or generated local artifacts.
- Prefer review-first behavior for new automation paths.
- Keep auto-send decisions explainable through policy reasons, risk level, and send-attempt status.
- Add or update tests when changing policy, queue state, event tickets, retry, or contact matching.
- Update documentation when changing user-visible flows or architecture.

## Good First Areas

- Documentation improvements.
- UI polish that improves clarity without changing product logic.
- Backend tests for policy and status transitions.
- Android ViewModel tests for queue, contacts, events, and logs.
- Manual test matrix updates for real-device behavior.

## Pull Request Checklist

- No secrets or private data are included.
- Android compile check passes or the failure is documented.
- Backend tests pass or the failure is documented.
- README/docs are updated when behavior changes.
- Safety and review behavior remains visible to the user.

## Security Issues

Please do not open public issues for security problems. Follow [SECURITY.md](SECURITY.md).
