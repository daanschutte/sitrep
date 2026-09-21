FROM eclipse-temurin:25-jdk AS builder

WORKDIR /builder

COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

RUN cp target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted


FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application

RUN groupadd -r sitrep && useradd -r -g sitrep -s /sbin/nologin sitrep

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Training run to populate the AOT cache. Flyway/schema-validate are disabled here only
RUN POSTGRES_URL=jdbc:postgresql://localhost:5432/sitrep \
    APP_USER_USERNAME=training \
    APP_USER_PASSWORD=training \
    SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=-1 \
    SPRING_FLYWAY_ENABLED=false \
    SPRING_JPA_HIBERNATE_DDL_AUTO=none \
    SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect \
    java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar application.jar

USER sitrep

ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]