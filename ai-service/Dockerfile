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

COPY ai-service/src ai-service/src
RUN ./mvnw -pl ai-service -am package -DskipTests

# Extract into layers instead of copying one fat jar
RUN java -Djarmode=tools -jar ai-service/target/*.jar extract --layers --launcher --destination ai-service/extracted

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Order matters: least-changing first for best cache reuse
COPY --from=build /workspace/ai-service/extracted/dependencies/ ./
COPY --from=build /workspace/ai-service/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/ai-service/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/ai-service/extracted/application/ ./

EXPOSE 8084
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

