FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY config ./config
COPY src ./src
RUN mvn -DskipTests package \
    && find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -exec cp {} /tmp/wxn-chatbot.jar \;

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /tmp/wxn-chatbot.jar /app/wxn-chatbot.jar

EXPOSE 4001
ENTRYPOINT ["java", "-jar", "/app/wxn-chatbot.jar"]
