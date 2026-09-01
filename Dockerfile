# Etapa de construcción de la app
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa final con MySQL + App
FROM eclipse-temurin:17-jre

# Instalar MySQL y dependencias necesarias
RUN apt-get update && \
    apt-get install -y mysql-server && \
    rm -rf /var/lib/apt/lists/*

# Configurar MySQL - Crear directorios necesarios
RUN mkdir -p /var/run/mysqld && \
    mkdir -p /var/lib/mysql && \
    chown -R mysql:mysql /var/run/mysqld && \
    chown -R mysql:mysql /var/lib/mysql && \
    chmod -R 755 /var/run/mysqld && \
    chmod -R 755 /var/lib/mysql

# Copiar script de inicialización
COPY init.sql /docker-entrypoint-initdb.d/
COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/ 2>/dev/null || true

# Copiar la aplicación
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Copiar script de inicio
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8080

ENTRYPOINT ["/start.sh"]