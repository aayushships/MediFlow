# MediFlow 🏥

MediFlow is a clinic-management backend built with Spring Boot. It supports patient registration, appointment workflows, JWT authentication, PostgreSQL persistence, and asynchronous notifications through RabbitMQ.

## Tech stack

- Java 21 and Spring Boot
- Spring Data JPA and PostgreSQL
- RabbitMQ for asynchronous messaging
- Docker and Docker Compose
- JWT authentication
- Maven

## Run with Docker Compose

You need **Docker Desktop** (Windows/macOS) or Docker Engine plus the Docker Compose plugin (Linux). You do not need to install Java, Maven, PostgreSQL, or RabbitMQ on the host.

1. Clone the repository and enter its directory:

   ```bash
   git clone https://github.com/aayushships/MediFlow.git
   cd MediFlow
   ```

2. Create your local environment file:

   **Windows PowerShell**
   ```powershell
   Copy-Item .env.example .env
   ```

   **macOS/Linux**
   ```bash
   cp .env.example .env
   ```

3. Edit `.env`. Set strong unique values for `DB_PASSWORD`, `RABBITMQ_PASSWORD`, and `JWT_SECRET`. For Gmail notifications, set `MAIL_USERNAME` and a Gmail **App Password** in `MAIL_PASSWORD`; do not use your normal Gmail password. If email is not configured, the app can still start, but email delivery will fail until valid credentials are supplied.

4. Build and start the stack:

   ```bash
   docker compose up -d --build
   ```

   Compose starts PostgreSQL and RabbitMQ, waits for their health checks, then starts the Spring Boot app. The first build can take a few minutes.

5. Check container status and application logs:

   ```bash
   docker compose ps
   docker compose logs --tail=100 app
   ```

   Look for `Started MediFlowApplication` in the app logs.

6. The API is available at **http://localhost:8080**. If Springdoc/Swagger UI is included and enabled in this build, open **http://localhost:8080/swagger-ui/index.html**.

### Services and ports

| Service | Host address | Purpose |
| --- | --- | --- |
| Spring Boot API | `localhost:8080` | REST API |
| PostgreSQL | `localhost:5433` | Database (host access; app uses the internal Compose network) |
| RabbitMQ management UI | `http://localhost:15672` | Queue/exchange management; sign in using `RABBITMQ_USER` and `RABBITMQ_PASSWORD` from `.env` |

The app connects to services using Compose service names (`postgres` and `rabbitmq`), not `localhost`. RabbitMQ is included in the stack, so a separate host RabbitMQ installation is not required.

### Useful commands

```bash
# Follow app logs
docker compose logs -f app

# Stop the containers (keeps named volumes/data)
docker compose down

# Stop and delete containers AND database/message-broker data
# WARNING: this permanently deletes the named volume data
docker compose down -v
```

### Notes

- Keep `.env` private. It is ignored by Git; commit only `.env.example`.
- Use new, strong passwords and a long random JWT secret for each deployment.
- The database and RabbitMQ data persist in named Docker volumes.
- If you change PostgreSQL credentials after the database volume has already been initialized, changing `.env` alone does not change the existing database user's password. Update the database user password or recreate the volume only if you are willing to lose its data.
- This Compose setup is intended for local development/demo use. Before public production deployment, add HTTPS, restrict exposed database/broker ports, configure production secrets, and review health/readiness and backup policies.

## Current features

- Patient registration and retrieval
- Input validation and centralized exception handling
- Appointment scheduling and lifecycle operations
- JWT-based authentication/authorization
- PostgreSQL persistence
- RabbitMQ-backed asynchronous notifications
- Docker Compose development stack

## Project status

🚧 Under active development.
