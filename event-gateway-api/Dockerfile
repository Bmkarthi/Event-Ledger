FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy parent pom and modules
COPY pom.xml /app/pom.xml
COPY common /app/common
COPY event-gateway-api /app/event-gateway-api

RUN mvn -q clean package -DskipTests -pl event-gateway-api -am

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/event-gateway-api/target/event-gateway-api-1.0.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

