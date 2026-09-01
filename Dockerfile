# Etapa de construcción de la app
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa final con MySQL + App
FROM eclipse-temurin:17-jre

# Instalar MySQL
RUN apt-get update && \
    apt-get install -y mysql-server && \
    rm -rf /var/lib/apt/lists/*

# Configurar MySQL
RUN mkdir -p /var/run/mysqld && \
    chown -R mysql:mysql /var/run/mysqld && \
    chown -R mysql:mysql /var/lib/mysql

# Copiar script de inicialización
COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/

# Copiar la aplicación
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Iniciar MySQL y luego la app TODO EN UN COMANDO
CMD service mysql start && \
    sleep 10 && \
    mysql -u root -proot123 < /docker-entrypoint-initdb.d/schema.sql && \
    java -jar /app/app.jar