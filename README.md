# Enterprise Trading Platform (Firodha) — Chennai Capstone SE6 Team 2

An institutional-grade, multi-asset trading platform featuring real-time market data streaming, order management, execution processing, AI-powered quantitative trade advice, and an intelligent conversational trading assistant.

---

## 1. Architecture Overview

```
                      ┌──────────────────────────────────────┐
                      │    Angular Web UI (trade-ui)        │
                      │         http://localhost:4200        │
                      └──────────────────┬───────────────────┘
                                         │ REST / JWT
               ┌─────────────────────────┴─────────────────────────┐
               ▼                                                   ▼
┌──────────────────────────────┐                ┌──────────────────────────────┐
│   Auth Service (NestJS)      │                │   Trade API (Spring Boot)    │
│    http://localhost:3000     │                │    http://localhost:8081     │
└──────────────┬───────────────┘                └──────────────┬───────────────┘
               │                                               │
               │ PostgreSQL (port 8095/5432)                   │ Kafka (9092)
               ▼                                               ▼
┌──────────────────────────────┐                ┌──────────────────────────────┐
│ PostgreSQL 16 (etp-postgres) │                │  Apache Kafka Event Stream   │
│  schemas: tradingsystem      │                │  topics: market-data, orders │
└──────────────────────────────┘                └──────────────┬───────────────┘
                                                               │
               ┌───────────────────────────────────────────────┴───────────────┐
               ▼                                                               ▼
┌──────────────────────────────┐                                ┌──────────────────────────────┐
│ Trade Executor (Java/Spring) │                                │  DuckDB Analytics ETL (Py)  │
│    http://localhost:8082     │                                │  Incremental pipeline        │
└──────────────────────────────┘                                └──────────────────────────────┘
```

---

## 2. Port & Service Matrix

| Service | Technology | Port (Host) | Health / Status Endpoint |
| :--- | :--- | :--- | :--- |
| **PostgreSQL Database** | PostgreSQL 16 | `8095` (maps to 5432) | `pg_isready -U postgres -d trading` |
| **Auth Service** | Node.js (NestJS) | `3000` | `http://localhost:3000/auth/me` |
| **Trade API** | Java 21 / Spring Boot | `8081` | `http://localhost:8081/actuator/health` |
| **Trade Executor** | Java 21 / Spring Boot | `8082` | `http://localhost:8082/actuator/health` |
| **Frontend UI** | Angular 21 | `4200` | `http://localhost:4200` |
| **Auth Stub** *(Dev Mock)* | Node.js / Express | `8090` | `http://localhost:8090/login` |
| **Apache Kafka** | Apache Kafka | `9092` | Broker connection test |
| **Analytics ETL** | Python / DuckDB | Local Host CLI | `inspect_db.py` |

---

## 3. Prerequisites

Before running the platform, ensure the following tools are installed:
* **Docker & Docker Compose** (v20+)
* **Java 21 (JDK)** & **Maven** (3.9+)
* **Node.js** (v20+) & **npm** (v10+)
* **Python** (v3.10+) & **pip**
* **Angular CLI** (`npm install -g @angular/cli`)

---

## 4. Environment Setup

Copy `.env.example` to `.env` in the root directory:

```bash
# Windows PowerShell
Copy-Item .env.example .env

# Linux / macOS
cp .env.example .env
```

Review `.env` to verify your database credentials, shared JWT secret, and email configuration:
```env
POSTGRES_DB=trading
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password_here
JWT_SECRET=development-only-shared-secret-change-me
JWT_ISSUER=auth-service
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

---

## 5. Quickstart: Running via Docker Compose

### 5.1. Start Database Only (Recommended for Local Dev)
If you prefer running services locally for debugging while keeping the database in Docker:
```bash
docker compose up -d postgres
```

### 5.2. Start the Full Containerized Platform
To run PostgreSQL, Auth Service, and Trade API together in containers:
```bash
docker compose up -d --build
```

To stop containers:
```bash
docker compose down
```
*(To reset the database volume completely, use `docker compose down -v`)*.

---

## 6. Service-by-Service Local Run Commands

### 6.1. Database (PostgreSQL)
Start the PostgreSQL container:
```bash
docker compose up -d postgres
```
Verify the container is healthy:
```bash
docker ps
```

---

### 6.2. Auth Service (Port 3000)
The authentication microservice handles registration, login, JWT token issuance, and password hashing (Argon2/Bcrypt).

```bash
cd Services/auth-service

# Install dependencies
npm install

# Start in development mode with hot reload
npm run start:dev
```
*Accessible at: `http://localhost:3000`*  
*Swagger Documentation: `http://localhost:3000/api`*

---

### 6.3. Trade API (Port 8081)
The primary backend handling orders, accounts, portfolios, technical indicator calculations, Gemini AI signals, and the conversational assistant.

```bash
cd Services/trade-api

# Build and run with Maven Wrapper
./mvnw clean spring-boot:run
```
*(On Windows PowerShell, use `.\mvnw.cmd clean spring-boot:run`)*  
*Accessible at: `http://localhost:8081`*

---

### 6.4. Trade Executor (Port 8082)
The trade execution service that matches orders and simulates exchange execution fills.

```bash
cd Services/trade-executor

# Build and run with Maven Wrapper
./mvnw clean spring-boot:run
```
*(On Windows PowerShell, use `.\mvnw.cmd clean spring-boot:run`)*  
*Accessible at: `http://localhost:8082`*

---

### 6.5. Frontend Web UI (Port 4200)
The Angular web application featuring trading dashboards, real-time charts, order tickets, AI Signals, and the AI Chatbot widget.

```bash
cd Frontend/trade-ui

# Install dependencies
npm install

# Start development server
npm start
# or: ng serve --open
```
*Accessible at: `http://localhost:4200`*

---

### 6.6. Analytics & ETL Pipeline (Python + DuckDB)
The incremental ETL pipeline extracts orders and executions from PostgreSQL, transforms them, and loads them into a DuckDB dimensional data warehouse.

```bash
cd ETL/sprint-07-analytics-etl

# Create virtual environment & activate
python -m venv venv
# Windows:
.\venv\Scripts\activate
# Linux/macOS:
source venv/bin/activate

# Install requirements
pip install -r requirements.txt

# Run complete ETL pipeline (Extract -> Transform -> Load)
python -m analytics_etl.pipeline.pipeline

# Inspect DuckDB tables
python inspect_db.py
```

---

### 6.7. Kafka Message Bus Setup (Optional / Streaming)
If running Apache Kafka locally:
```bash
# Start Kafka broker (see Infrastructure/Kafka/scripts/run-kafka-docker.txt)
# Create necessary topics:
bash Infrastructure/Kafka/scripts/create-kafka-topic.sh
```
Topics: `market-data`, `orders`, `trade-events`, `trade-signals`.

---

## 7. Running Tests

### Frontend Tests (`trade-ui`)
```bash
cd Frontend/trade-ui
npm test                 # Run Angular unit tests with Vitest/Karma
npm run test:coverage    # Unit test coverage report
npm run e2e              # Playwright End-to-End tests
```

### Backend Trade API Tests (`trade-api`)
```bash
cd Services/trade-api
./mvnw test              # Run JUnit unit & integration tests
```

### Auth Service Tests (`auth-service`)
```bash
cd Services/auth-service
npm run test             # Run Vitest unit tests
npm run test:e2e         # Run end-to-end authentication tests
```

### Analytics ETL Tests (`sprint-07-analytics-etl`)
```bash
cd ETL/sprint-07-analytics-etl
pytest                   # Run ETL data quality and pipeline tests
```

---

## 8. Key Features & Documentation

* **AI Signals & Assistant Architecture Guide**: Complete technical documentation for the Wilder's RSI(14) + EMA(9/21) signal engine, Gemini LLM gateway, and 5-layer safety guardrails.  
  👉 See: [`AI_SIGNALS_AND_ASSISTANT_MASTER_GUIDE.md`](./AI_SIGNALS_AND_ASSISTANT_MASTER_GUIDE.md)
* **API Specifications**: OpenAPI schemas located in [`Contracts/`](./Contracts/).
* **Database Migrations**: Located in [`Database/Postgres/migrations/`](./Database/Postgres/migrations/) and [`Services/trade-api/src/main/resources/db/migration/`](./Services/trade-api/src/main/resources/db/migration/).