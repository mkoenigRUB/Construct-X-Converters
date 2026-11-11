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
# Ontologien: Source of truth = GitHub-Repo (kein lokaler COPY aus dem Build-Kontext).
# Auf den Tag v0.5.0 gepinnt -> reproduzierbare Builds. Bump = bewusster ARG-Wechsel.
ARG ONTOLOGIES_TARBALL=https://github.com/mkoenigRUB/Construct-X-Ontologies/archive/refs/tags/v0.5.0.tar.gz
ADD ${ONTOLOGIES_TARBALL} /tmp/ontologies/
RUN mkdir -p /app/ontologies && \
    tar xzf /tmp/ontologies/v0.5.0.tar.gz -C /tmp/ontologies/ && \
    cd /tmp/ontologies/Construct-X-Ontologies-0.5.0 && \
    cp -R construction-schedule breakdown-structure gaeb-boq \
          construction-progress-report construction-site \
          construction-delivery built-asset link-ontologies notification-content \
          MANIFEST.json /app/ontologies/ && \
    rm -rf /tmp/ontologies
ENV CONX_ONTOLOGY_ROOT=/app/ontologies
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
