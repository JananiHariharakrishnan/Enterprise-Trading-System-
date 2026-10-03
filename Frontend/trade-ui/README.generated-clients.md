# Generated API Clients

This folder contains generated TypeScript API clients for two OpenAPI contracts.

## Inputs

- `../../Contracts` -> output: `src/app/generated/auth`
- `../../Services` -> output: `src/app/generated/trade`

## Regeneration command

Run from `trade-ui`:

```bash
npm run generate:contracts
```

You can also regenerate one client:

```bash
npm run generate:contracts:auth
npm run generate:contracts:trade
```

## Rules

- Do not edit any file under `src/app/generated/**`.
- Keep generated output committed to git so contract changes are visible in diffs.
- If generation fails on spec validation while contracts are known-good, this repo intentionally sets `skipValidateSpec=true` in `openapitools.json`.
- Put adapters/wrappers outside generated code (for example in `src/app/services`).
