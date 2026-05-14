# Security Policy

WA Agent Pro Control Center handles private communication data. Please treat all message content, local databases, logs, screenshots, tokens, and backend URLs as sensitive.

## Supported Versions

This project is currently an active prototype. Security fixes should target the `main` branch unless a separate release branch exists.

## Reporting A Vulnerability

Please report security issues privately by email:

**bassam.sallam.de@gmail.com**

Do not open a public GitHub issue for vulnerabilities that include:

- leaked tokens, keys, backend URLs, or credentials;
- private WhatsApp message content;
- local database contents;
- screenshots with private names or phone numbers;
- auth bypass, unsafe auto-send behavior, or data exposure.

When reporting, include:

- a short description of the issue;
- affected files, endpoint, or Android component if known;
- reproduction steps when safe to share;
- whether private data may be exposed.

## Sensitive Data Rules

- Do not attach real `.env` files.
- Do not attach `whatsapp_agent.db` or other local databases.
- Redact phone numbers, contact names, message bodies, and tokens.
- Prefer minimal synthetic examples.

## Project Security Notes

The current Android path uses notification access, RemoteInput, and an optional Accessibility fallback. These are powerful device capabilities and must remain visible, consent-based, and controllable by the user. The project should default toward review and safety for unknown or sensitive conversations.
