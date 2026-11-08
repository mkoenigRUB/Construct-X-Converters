FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY construct-x-sdk construct-x-sdk
RUN mvn -B -f construct-x-sdk/pom.xml install -DskipTests
WORKDIR /workspace/rdf-converter-service
COPY rdf-converter-service/pom.xml .
RUN mvn -B dependency:go-offline
COPY rdf-converter-service/src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/rdf-converter-service/target/construct-x-rdf-service.jar app.jar
COPY construction-schedule /app/ontologies/construction-schedule
COPY breakdown-structure /app/ontologies/breakdown-structure
COPY gaeb-boq /app/ontologies/gaeb-boq
COPY construction-progress-report /app/ontologies/construction-progress-report
COPY construction-site /app/ontologies/construction-site
COPY construction-delivery /app/ontologies/construction-delivery
COPY built-asset /app/ontologies/built-asset
COPY link-ontologies /app/ontologies/link-ontologies
COPY MANIFEST.json /app/ontologies/MANIFEST.json
ENV CONX_ONTOLOGY_ROOT=/app/ontologies
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
