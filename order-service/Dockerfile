# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY common-avro ./common-avro
COPY order-service ./order-service
COPY inventory-service ./inventory-service
COPY notification-service ./notification-service
COPY api-gateway ./api-gateway

RUN mvn -q -pl order-service -am package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/order-service/target/order-service.jar app.jar

USER app
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]