## Summary
<!-- What does this change do and why? Link the ticket. -->

## Checklist
- [ ] Follows the rules in `CLAUDE.md` (envelope, exceptions, no hard-coded values, logging, transactions)
- [ ] Every new class and method has Javadoc
- [ ] Tests added/updated; `./mvnw clean verify` is green (coverage gate passed)
- [ ] Flyway migration added for schema changes (no edits to applied migrations)
- [ ] New configuration documented in `README.md` and set for every profile
- [ ] No secrets, credentials or PII in code, config or logs
