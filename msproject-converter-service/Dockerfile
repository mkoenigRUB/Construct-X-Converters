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
# Ontologien: Source of truth = GitHub-Repo (kein lokaler COPY aus dem Build-Kontext).
# Repo hat keine Tags -> floating main; jeder Build holt die aktuellen Dateien.
ARG ONTOLOGIES_TARBALL=https://github.com/mkoenigRUB/Construct-X-Ontologies/archive/main.tar.gz
ADD ${ONTOLOGIES_TARBALL} /tmp/ontologies/
RUN mkdir -p /app/ontologies && \
    tar xzf /tmp/ontologies/main.tar.gz -C /tmp/ontologies/ && \
    cp -R /tmp/ontologies/Construct-X-Ontologies-main/construction-schedule \
          /app/ontologies/construction-schedule && \
    rm -rf /tmp/ontologies
ENV CONX_ONTOLOGY_ROOT=/app/ontologies
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
