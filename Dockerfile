FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/attendancebackend.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
