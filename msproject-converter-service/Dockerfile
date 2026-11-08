FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY construct-x-sdk construct-x-sdk
RUN mvn -B -f construct-x-sdk/pom.xml install -DskipTests
WORKDIR /workspace/msproject-converter-service
COPY msproject-converter-service/pom.xml .
RUN mvn -B dependency:go-offline
COPY msproject-converter-service/src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/msproject-converter-service/target/msproject-converter-service-1.0.0.jar app.jar
COPY construction-schedule /app/ontologies/construction-schedule
ENV CONX_ONTOLOGY_ROOT=/app/ontologies
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
