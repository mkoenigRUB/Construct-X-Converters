# Architecture decisions and reference stack

**Status:** proposal; confirm before starting a new app. Respect existing project choices.

## Reference stack
- Next.js (App Router), React, TypeScript strict
- Tailwind CSS and shadcn/ui for accessible base components (verify component accessibility individually)
- PostgreSQL, Prisma *or* Drizzle (choose one after assessing schema and deployment requirements)
- Zod validation at external boundaries
- Auth.js for simple web authentication, or an external OIDC provider such as Keycloak for enterprise SSO (select one)
- Vitest for unit/integration, Playwright for browser E2E
- ESLint, formatter, GitHub Actions for CI
- Docker when needed for reproducible deployment

## Logical layers
1. `app/`: routes, layouts, server entry points
2. `components/`: accessible UI and reusable design system
3. `features/<feature>/`: feature views, actions, schemas, tests
4. `lib/auth/`: authentication and authorization policies
5. `lib/db/`: database client, query layer and migrations
6. `lib/integrations/`: outbound connectors, adapters and retry policies

Do not put permission decisions solely in the client. Keep business rules independently testable. Avoid prematurely splitting a modular monolith into microservices.

## API and data contracts
- Validate request bodies and query parameters; use consistent typed responses and error codes.
- All queries must be scope-filtered on the server and parameterized.
- Prefer idempotent operations for retries; record audit events for sensitive changes.
- Document schemas, migrations, indexing and backup/recovery expectations.

## Architectural decision records
For nontrivial choices, add `docs/decisions/NNN-title.md` with Context / Decision / Alternatives / Consequences.

## Deployment assumptions
[Hosting, environments dev/staging/prod, secrets, backups, observability, rollback, cost]
