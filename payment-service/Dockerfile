FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
COPY wait-for-temporal.sh wait-for-temporal.sh
RUN chmod +x wait-for-temporal.sh
EXPOSE 8080
ENTRYPOINT ["./wait-for-temporal.sh", "java", "-jar", "app.jar"]
