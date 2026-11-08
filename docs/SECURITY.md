# Security and privacy baseline

- Threat model: actors, assets, entry points, threat scenarios and mitigations before sensitive workflows.
- Authentication: established libraries/providers; secure cookie/session handling, CSRF defenses where relevant.
- Authorization: deny by default, server-side per-operation checks, resource ownership/tenant-scoped queries, tests for horizontal and vertical privilege escalation.
- Inputs: schema validation, output escaping, parameterized database queries; safe file upload content/type/size scanning and storage where relevant.
- Secrets: environment secrets only, never commit them; provide `.env.example` with placeholders only.
- Web defenses: security headers, transport TLS, rate limiting, dependency scanning, content security policy where feasible.
- Privacy: data minimization, retention/deletion rules, lawful basis and DPA/data residency review when applicable.
- Operations: structured logs excluding secrets, least-privilege service accounts, backups and restore tests, patch policy and incident workflow.
- External AI models: never send confidential project data, credentials or personal data to third parties without explicit approval and a suitable data-processing arrangement.

A checklist is not a security certification: obtain a separate security review before handling sensitive production data.
