FROM eclipse-temurin:17-jdk-noble AS builder
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY noone-core ./noone-core
COPY noone-server ./noone-server

RUN chmod +x gradlew
RUN ./gradlew --no-daemon :noone-server:bootJar -x test

FROM eclipse-temurin:17-jre-noble AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
EXPOSE 8888
COPY --from=builder /workspace/noone-server/build/libs/*.jar /app/app.jar
#COPY noone-server/build/libs/*.jar /app/app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
