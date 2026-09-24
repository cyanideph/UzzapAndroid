# Security Policy

## Reporting a vulnerability

Please do not disclose security vulnerabilities in public issues.

Use GitHub's private security reporting feature for this repository when available. Include:
- affected component/file
- reproduction steps
- impact
- relevant logs or proof of concept

Never include passwords, Supabase service-role keys, private tokens, keystores, or other credentials in an issue or pull request.

## Repository rules

- Only Supabase publishable/anon credentials may be present in the Android client.
- Supabase service-role/secret keys must remain server-side.
- Android signing keys and passwords must be stored only in GitHub Actions secrets.
