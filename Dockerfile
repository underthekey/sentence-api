FROM docker
COPY --from=docker/buildx-bin:latest /buildx /usr/libexec/docker/cli-plugins/docker-buildx

FROM openjdk:17-jdk
EXPOSE 443
ENV TZ Asia/Seoul
ADD ./build/libs/sentence-api-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]