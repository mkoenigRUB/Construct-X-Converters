FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY construct-x-sdk construct-x-sdk
RUN mvn -B -f construct-x-sdk/pom.xml install -DskipTests
WORKDIR /workspace/Converter
COPY Converter/pom.xml .
COPY Converter/core/pom.xml core/
COPY Converter/web/pom.xml web/
RUN mvn -B -f pom.xml dependency:go-offline
COPY Converter/core/src core/src
COPY Converter/web/src web/src
RUN mvn -B -f pom.xml package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/Converter/web/target/construct-x-converters-web.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
