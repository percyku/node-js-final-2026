FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# 先只複製 pom.xml 下載相依套件，程式碼變動時這層可以沿用快取
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# 測試需要連資料庫，不在 build image 時執行
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S livefit && adduser -S livefit -G livefit
COPY --from=build /app/target/livefit-0.0.1-SNAPSHOT.jar app.jar
USER livefit

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
