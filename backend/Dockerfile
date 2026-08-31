FROM maven:3.9.11-eclipse-temurin-21-noble AS build

WORKDIR /workspace

# Keep dependency resolution in a source-independent layer so ordinary code
# changes do not download the complete Maven dependency graph again.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Container image builds compile only production sources. The full test suite,
# including Testcontainers and native OR-Tools tests, runs on the host first.
COPY src/main ./src/main
RUN mvn -B -ntp -Dmaven.test.skip=true package \
    && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print -quit)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/application.jar

FROM eclipse-temurin:21-jre-noble AS runtime

WORKDIR /app

RUN groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --no-create-home \
        --home-dir /app --shell /usr/sbin/nologin appuser

COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
