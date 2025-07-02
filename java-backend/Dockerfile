# --------- STAGE 1: build with Maven ---------
FROM maven:3.9-eclipse-temurin AS build

WORKDIR /app

# Add build-related labels for documentation
LABEL description="Kafka Practice App"
LABEL maintainer="Bartu"
LABEL version="1.0.0"


# copy only pom first, download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy all sources and resources, compile and package, previously it was COPY .. .
COPY . .
RUN mvn package -DskipTests -B

# --------- STAGE 2: runtime --------- clipse-temurin:17-jre-jammy? JRE MI JDK MI
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user
RUN useradd -ms /bin/bash appuser
USER appuser

# copy fat‐jar from builder
COPY --from=build /app/target/App-1.0-SNAPSHOT.jar app.jar


EXPOSE 8080

ENTRYPOINT ["sh","-c","java -jar app.jar"]