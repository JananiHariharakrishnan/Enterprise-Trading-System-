# Trade API

## Local configuration

The app resolves datasource and server values from environment variables in
`src/main/resources/application.properties`.

It now loads env files from the most common local launch locations so the API
can be started from either the repository root or the `trade-api` folder.
It also accepts either the module-local `DB_*` variables or the shared
`../../Database/Postgres/.env` `POSTGRES_*` variables.

- Copy `.env.example` to `.env`.
- Set at least `DB_PASSWORD` to your local database password.
- Or keep using `../../Database/Postgres/.env`; the API will fall back to
  `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
  `POSTGRES_PASSWORD` when `DB_*` values are not present.

```properties
SPRING_APPLICATION_NAME=trade-api
SERVER_PORT=8888
DB_URL=jdbc:postgresql://localhost:5432/trading
DB_USER=postgres
DB_PASSWORD=your_password_here
DB_DRIVER=org.postgresql.Driver
```

`.env` is ignored by git; `.env.example` stays committed as the template.

## Story: Layered Architecture Guardrails

This project enforces a strict controller -> service -> mapper flow and keeps Sprint 5 trading rules independent from HTTP and persistence frameworks.

### What is enforced

- Controllers do not import mapper, SQL, JDBC, MyBatis, or persistence APIs.
- Services do not accept servlet, HTTP status/response wrapper, or `*Request` parameter types.
- Sprint 5 domain/support classes (`model`, `enums`, `exception`, `service.command`) do not import servlet, Spring, or MyBatis packages.

### Run checks

```powershell
Set-Location "C:\Users\Administrator\Desktop\projects\Chennai-Capstone-SE6-Team2\trade-api"
.\mvnw.cmd test -DskipTests=false
```

Architecture checks are implemented in:

- `src/test/java/com/tradingsystem/architecture/LayeringArchitectureTest.java`

## Story: MyBatis Mapper Safety

This project now uses MyBatis XML mappers under `src/main/resources/mapper` with bind parameters (`#{...}`) for all external values.

### What is enforced

- Mapper SQL must not use text interpolation (`${...}`) unless explicitly allowlisted.
- Any allowlisted `${...}` usage must have its fixed-list validator described in `manifest.env`.
- Mapper insert methods return affected row count (`int`), not `void`.

### MyBatis files

- `src/main/java/com/tradingsystem/mapper/AccountDataMapper.java`
- `src/main/java/com/tradingsystem/mapper/OrderDataMapper.java`
- `src/main/resources/mapper/AccountDataMapper.xml`
- `src/main/resources/mapper/OrderDataMapper.xml`

### Tests

- `src/test/java/com/tradingsystem/architecture/MapperSqlSafetyTest.java`
- `src/test/java/com/tradingsystem/mapper/MyBatisMapperIntegrationTest.java`

These tests validate:

- order row inserted and retrievable
- account and positions read correctly
- mapper returns affected row count
- duplicate `idempotency_key` surfaces a constraint violation


