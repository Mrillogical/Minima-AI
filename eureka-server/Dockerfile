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

COPY eureka-server/src eureka-server/src
RUN ./mvnw -pl eureka-server -am package -DskipTests

# Extract into layers instead of copying one fat jar
RUN java -Djarmode=tools -jar eureka-server/target/*.jar extract --layers --launcher --destination eureka-server/extracted

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Order matters: least-changing first for best cache reuse
COPY --from=build /workspace/eureka-server/extracted/dependencies/ ./
COPY --from=build /workspace/eureka-server/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/eureka-server/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/eureka-server/extracted/application/ ./

EXPOSE 8761
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

