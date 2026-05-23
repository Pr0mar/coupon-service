# Coupon Service

## Run

```bash
docker compose --profile app up --build -d
```

## Services

| Service | URL | Notes |
|---------|-----|-------|
| API | <http://localhost:8080> | Spring Boot 3.3 / Java 21 |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> | |
| Health | <http://localhost:8080/actuator/health> | |
| Prometheus scrape | <http://localhost:8080/actuator/prometheus> | raw metrics |
| PostgreSQL | `localhost:5432` | db `coupons`, user `app`, pass `secret` |
| Kibana | <http://localhost:5601> | data view `coupon-logs` |
| Prometheus | <http://localhost:9090> | |
| Grafana | <http://localhost:3000> | dashboard "Coupon Service", `admin` / `admin` |

## Tests

```bash
mvn test
```
