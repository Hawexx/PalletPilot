# 🏭 PalletPilot — Warehouse Control System

**Industrial-grade WCS** for automated warehouse operations: order management, pallet building, equipment control, and real-time monitoring.

## 🏗 Architecture

```
┌───────────────────────────────────────────────────────┐
│                   Next.js Frontend                     │
│          Dashboard · Orders · 3D Pallet View          │
└───────────────────────┬───────────────────────────────┘
                        │ REST + WebSocket
┌───────────────────────┴───────────────────────────────┐
│              Spring Boot 3 Backend (Java 21)           │
├────────────┬────────────┬────────────┬────────────────┤
│  Order     │  Equipment │  Slotting  │  Movu AS/RS    │
│  Service   │  Service   │  ABC/ABCD  │  Integration   │
├────────────┴────────────┴────────────┴────────────────┤
│  Security │ Audit Log │ Rate Limit │ Circuit Breaker  │
├───────────────────────────────────────────────────────┤
│              PostgreSQL  ·  Flyway Migrations          │
└───────────────────────────────────────────────────────┘
```

## ⚡ Quick Start

```bash
# 1. Clone
git clone <repo-url> && cd PalletPilot-main

# 2. Environment
cp .env.example .env   # Edit with your values

# 3. Docker (recommended)
docker compose up -d

# 4. Local Development
# Backend
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
# Frontend
npm install && npm run dev
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:8080/actuator/prometheus |

## 🔧 Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 14, TypeScript, Tailwind CSS |
| Backend | Spring Boot 3, Java 21, Gradle |
| Database | PostgreSQL 16, Flyway |
| Cache | Caffeine (in-process) |
| Monitoring | Micrometer + Prometheus |
| Logging | Logback (JSON in prod, colorful in dev) |
| Security | Spring Security, Rate Limiting, Audit Log |
| CI/CD | GitHub Actions |
| Container | Docker, Docker Compose |
| Hardware | Movu AS/RS, KUKA, Beckhoff PLC |

## 📁 Project Structure

```
PalletPilot-main/
├── backend/                  # Spring Boot API
│   ├── src/main/java/
│   │   └── com/palletpilot/wcs/
│   │       ├── application/  # Services & business logic
│   │       ├── domain/       # Entities & value objects
│   │       └── infrastructure/
│   │           ├── web/      # REST controllers
│   │           ├── movu/     # Movu AS/RS integration
│   │           ├── config/   # Metrics, cache, security
│   │           └── persistence/  # JPA repositories
│   └── src/test/java/        # 49 unit tests
├── src/                      # Next.js frontend
│   ├── app/                  # Pages & routing
│   ├── components/           # UI components
│   └── hooks/                # Custom hooks
├── docker-compose.yml        # Full-stack orchestration
└── .github/workflows/ci.yml  # CI/CD pipeline
```

## 🧪 Testing

```bash
cd backend
./gradlew test          # 49 tests
```

## 🔐 Spring Profiles

| Profile | Usage |
|---------|-------|
| `dev` | Simulation mode, debug logging, relaxed rate limits |
| `prod` | Real hardware, Flyway, graceful shutdown, JSON logging |

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 📊 Monitoring

- **Health:** `GET /actuator/health` (Movu + PLC health indicators)
- **Metrics:** `GET /actuator/prometheus` (orders, commands, failures, state changes)
- **Audit:** Database table `audit_log` with correlation IDs

## 📜 License

Proprietary — FonTek © 2026
