# PalletPilot WCS - Java Backend

This is the Java Backend for the PalletPilot Warehouse Control System. It is built with **Spring Boot 3**, **Java 21**, and **PostgreSQL**.

## Prerequisites
1.  **Java JDK 21** installed.
2.  **PostgreSQL** running on port 5432.
3.  **Maven** or **Gradle** installed (or use the wrapper).

## Project Structure
*   `domain`: Core business logic (Pallet Algorithm, State Machines).
*   `application`: Service layer (Orchestration).
*   `infrastructure`: Adapters for Database, Web, and Real/Simulation Hardware.
*   `resources/db/migration`: Flyway SQL scripts for schema management.

## Setup Instructions

### 1. Database Setup
Create a PostgreSQL database named `palletpilot`:
```sql
CREATE DATABASE palletpilot;
```
Configure your credentials in `src/main/resources/application.properties` (or add the file if missing):
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/palletpilot
spring.datasource.username=postgres
spring.datasource.password=yourpassword
app.simulation.enabled=true
```

### 2. Run the Application
From the `backend` directory:
```bash
./gradlew bootRun
```
This will:
*   Start the Spring Boot server on port 8080.
*   Automatically run Flyway migrations to create tables.
*   Initialize the **Simulation Mode** for equipment.

### 3. API Endpoints (Swagger)
Once running, verify the API at:
`http://localhost:8080/swagger-ui.html`

*   **POST** `/api/pallets/plan/{orderId}`: Trigger the Deterministic Pallet Algorithm.
*   **GET** `/api/pallets`: View all generated pallets.
