FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy parent pom and modules
COPY pom.xml /app/pom.xml
COPY common /app/common
COPY account-service /app/account-service

RUN mvn -q clean package -DskipTests -pl account-service -am

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/account-service/target/account-service-1.0.0.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

