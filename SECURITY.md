# Security Policy

## Before publishing this repository

Do not commit `.env`, database exports, GeoIP databases, private keys, certificates, tokens, or production passwords. Copy `.env.example` to `.env` only on your own machine, then set a unique `JWT_SECRET` with at least 32 bytes of randomness.

The passwords in `docker-compose.yml` are local-development defaults. Change them and do not expose PostgreSQL, Redis, Prometheus, or Grafana ports directly to the public internet in a production deployment.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Contact the repository owner privately and include a clear reproduction path and impact summary.
