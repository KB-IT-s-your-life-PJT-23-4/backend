# Gradle로 war 빌드
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :core:war --no-daemon

# Tomcat에 war 배포
FROM tomcat:9.0-jdk17
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/core/build/libs/project-core.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]