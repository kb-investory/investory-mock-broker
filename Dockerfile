FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle war --no-daemon

FROM tomcat:9-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY --from=build /app/build/libs/investory-mock-broker.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
