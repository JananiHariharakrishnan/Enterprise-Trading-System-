# Argon2 Cost Selection

This document explains the Argon2 cost parameters used by the auth-service and how to tune them for approx. 100ms verification latency on deployment hardware.

Defaults used in code

- `ARGON2_TIME`: 3
- `ARGON2_MEMORY_KB`: 32768 (32 MB)  <-- recommended for ~100ms on our target hardware
- `ARGON2_PARALLELISM`: 1

These defaults are the values applied by `createCredential` and `verifyPassword` in `src/app/auth/repository/auth.repository.ts` when no environment variables are set.

Why target ~100ms?

- A password hash must be slow enough to make offline brute-force expensive (more CPU/time per guess).
- 100ms is a practical compromise: it adds noticeable cost to attackers while keeping interactive login latency low and limiting risk of login-based DoS.

How to measure and pick costs

1. Use the bundled measurement script to time a single `argon2.verify` run with your chosen parameters:

```bash
cd services/auth-service
npm run measure-argon2
```

2. Adjust environment variables and re-run until the measured `duration_ms` is near your target (e.g., 100 ms):

```powershell
$env:ARGON2_TIME=4; $env:ARGON2_MEMORY_KB=131072; npm run measure-argon2
```

3. When you choose final parameters, export them in your deployment environment (CI/CD or runtime) as `ARGON2_TIME`, `ARGON2_MEMORY_KB`, and `ARGON2_PARALLELISM`.

Notes and operational guidance

- Memory requirement (`ARGON2_MEMORY_KB`) should be chosen to leverage available RAM on auth nodes while keeping within container limits.
- Parallelism should match the number of vCPUs available to the process for best CPU utilisation.
- Run measurements during deployment on representative hardware rather than developer laptops; results vary widely across CPU generations.
- Add the measurement script to any performance benchmark step in CI that runs on target hardware.

Example measured results on the local machine used for tuning:

- `time=2, memory=65536` -> duration_ms= 134
- `time=3, memory=32768` -> duration_ms= 91
- `time=3, memory=49152` -> duration_ms= 138

Based on these runs the recommended starting environment variables to target ~100ms verification are:

```
ARGON2_TIME=3
ARGON2_MEMORY_KB=32768
ARGON2_PARALLELISM=1
```

Measure on representative deployment hardware and adjust `ARGON2_MEMORY_KB` (memory) or `ARGON2_TIME` (iterations) to hit the target latency.

The shipped defaults are a starting point, not a guarantee; production deploys should set all three Argon environment variables explicitly after measurement on the actual deployment class.

Security note

- Never store plaintext passwords.
- Avoid logging or serialising password fields; use the central logger which redacts sensitive keys.
