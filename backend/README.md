# WebAux backend

Java 21 modular monolith. Spring Boot 3.5, Spring MVC, Spring Security, Spring Data JPA, Flyway, PostgreSQL, JWT in HttpOnly cookies.

```bash
mvn spring-boot:run
mvn test
```

Configuration is in `src/main/resources/application.yml`. Every secret comes from the environment. See the repository `.env.example`.

OpenAPI is served at `/swagger-ui.html` when the process is running.

`mvn test` needs Docker. On Docker Engine 29 the Java client must request API 1.44 or newer. The Surefire configuration already passes `-Dapi.version=1.44`.

`GET /render/{slug}` returns the public HTML document used by the production Nginx route, including the title, description, and Open Graph tags. `GET /api/v1/public/events/{slug}` returns the same publication as JSON for the React renderer.

Media is stored under `PRISM_MEDIA_STORAGE_PATH` (default `./data/media`). The Docker image uses `/data/media` and Compose mounts a named volume there.

Scheduled jobs (expiry, reminders, pending-payment cleanup, outbox, media retention) take a PostgreSQL advisory lock so two instances do not process the same pass. Set `prism.scheduling.enabled=false` to disable them. Tests do.
