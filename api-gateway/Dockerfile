# to build the code, using maven with jdk 21 installation as base image
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B # to cache all the dependency for faster processing

COPY src ./src

RUN mvn clean package

# To run the code, using jdk 21 runtime
FROM maven:3.9.9-eclipse-temurin-21 AS runner

WORKDIR /app

COPY --from=builder ./app/target/api-gateway-0.0.1-SNAPSHOT.jar ./app.jar

# exposing the port where our app is running
EXPOSE 4004

ENTRYPOINT ["java", "-jar", "app.jar"]