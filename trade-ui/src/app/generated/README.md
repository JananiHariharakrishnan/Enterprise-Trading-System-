# Generated API Clients

This directory contains auto-generated TypeScript API clients for the trading platform services.

## Overview

The API clients are generated from the OpenAPI 3.1 contracts:
- **Auth Client**: `src/app/generated/auth/` - Generated from `contracts/auth-api.yaml`
- **Trade Client**: `src/app/generated/trade/` - Generated from `trade-api/src/main/resources/trade-api.yaml`

## Regenerating Clients

The OpenAPI Generator CLI is pinned to version 7.8.0 to ensure consistent output across the team.

### Regenerate all clients
```bash
npm run generate:contracts
```

This command will:
1. Clean existing generated directories
2. Generate the auth API client
3. Generate the trade API client

### Regenerate specific client
```bash
npm run generate:contracts:auth    # Auth API client only
npm run generate:contracts:trade   # Trade API client only
```

## Configuration

The generator configuration is in `openapitools.json`:
- Generator: `typescript-fetch` (fetch-based HTTP client)
- Target: ES6 with TypeScript interfaces
- Skip Validation: `true` (because contracts use `skipValidateSpec`)

## Important Notes

- **Committed, Not Ignored**: Generated files are committed to version control. A clone builds without Java or network.
- **Read-Only**: Never manually edit files in the generated directories. Changes will be overwritten on regeneration.
- **Contract First**: When contracts change, regenerate immediately and commit both the new contract and the generated client in the same commit.
- **Wrapper Services**: If the generated API shape is awkward, wrap it in a service outside this directory rather than editing the generated code.

## Usage

Import types and APIs from the generated directories:

```typescript
// Auth API
import { LoginRequest, UserResponse } from '../generated/auth';
import { DefaultApi as AuthApi } from '../generated/auth';

// Trade API
import { AccountResponse, OrderResponse } from '../generated/trade';
import { AccountsApi } from '../generated/trade';
```

## Validation Rules

When regenerating, the validator checks:
- OpenAPI 3.1 specification compliance
- Required fields presence
- Type correctness

The `skipValidateSpec` flag is necessary because both contracts are valid but don't include the `info.license.identifier` field (which OpenAPI 3.1 makes optional).

## References

- [OpenAPI Generator Documentation](https://openapi-generator.tech/)
- [TypeScript-Fetch Generator](https://openapi-generator.tech/docs/generators/typescript-fetch)
- [Trading Platform Contracts](../../contracts/)
