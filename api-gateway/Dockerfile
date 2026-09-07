FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY url-service/pom.xml url-service/
COPY analytics-service/pom.xml analytics-service/
COPY ai-service/pom.xml ai-service/

COPY .mvn .mvn
COPY mvnw .

COPY api-gateway/src api-gateway/src
RUN ./mvnw -pl api-gateway -am package -DskipTests

# Extract into layers instead of copying one fat jar
RUN java -Djarmode=tools -jar api-gateway/target/*.jar extract --layers --launcher --destination api-gateway/extracted

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Order matters: least-changing first for best cache reuse
COPY --from=build /workspace/api-gateway/extracted/dependencies/ ./
COPY --from=build /workspace/api-gateway/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/api-gateway/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/api-gateway/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

