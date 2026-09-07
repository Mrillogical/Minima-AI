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

COPY auth-service/src auth-service/src
RUN ./mvnw -pl auth-service -am package -DskipTests

# Extract into layers instead of copying one fat jar
RUN java -Djarmode=tools -jar auth-service/target/*.jar extract --layers --launcher --destination auth-service/extracted

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Order matters: least-changing first for best cache reuse
COPY --from=build /workspace/auth-service/extracted/dependencies/ ./
COPY --from=build /workspace/auth-service/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/auth-service/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/auth-service/extracted/application/ ./

EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

