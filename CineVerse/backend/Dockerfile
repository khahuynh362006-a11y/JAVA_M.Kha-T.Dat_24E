FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S cineverse && adduser -S cineverse -G cineverse
WORKDIR /app
RUN mkdir media uploads && chown -R cineverse:cineverse /app
COPY --from=build /build/target/cineverse-1.0.0.jar app.jar
USER cineverse
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
