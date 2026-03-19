FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
EXPOSE 8888
COPY noone-server/build/libs/*.jar /app/app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
