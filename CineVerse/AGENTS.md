# CineVerse coding rules

- Follow the modular monolith architecture documented in docs/ARCHITECTURE.md.
- Keep Java business logic in services; expose DTOs, never JPA entities.
- No hardcoded production secrets or production mock payments.
- Every visible UI action must work. Validate all forms and API inputs.
- Apply role and object authorization; use Flyway migrations.
- Customer-facing UI is Vietnamese; keep centralized design tokens consistent.
- Build and run relevant tests after meaningful changes; preserve working code.
- Do not silently ignore errors or fabricate test evidence.
- Document architecture decisions and keep TASKS.md accurate.
- Use readable maintainable code and avoid unnecessary dependencies.
- No pirated media. Record media licenses and attribution.
- Fix root causes, not cosmetic symptoms.
