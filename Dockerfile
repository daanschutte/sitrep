FROM eclipse-temurin:25-jdk AS builder

WORKDIR /builder

COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted


FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application

RUN groupadd -r sitrep && useradd -r -g sitrep -s /sbin/nologin sitrep

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

USER sitrep

ENTRYPOINT ["java", "-jar", "application.jar"]