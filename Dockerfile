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

# Configurar MySQL
RUN mkdir -p /var/run/mysqld && \
    mkdir -p /var/lib/mysql && \
    chown -R mysql:mysql /var/run/mysqld && \
    chown -R mysql:mysql /var/lib/mysql && \
    chmod -R 755 /var/run/mysqld && \
    chmod -R 755 /var/lib/mysql

# Directorio para scripts de inicialización
RUN mkdir -p /docker-entrypoint-initdb.d/

# Copiar schema.sql
COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/

# Copiar la aplicación
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# EJECUTAR TODO EN UN COMANDO (sin script externo)
CMD bash -c "mysqld_safe --bind-address=0.0.0.0 --datadir=/var/lib/mysql & \
    echo '⏳ Esperando a que MySQL esté listo...' && \
    sleep 15 && \
    echo '📦 Creando base de datos...' && \
    mysql -u root -e 'CREATE DATABASE IF NOT EXISTS crud_usuarios;' && \
    mysql -u root crud_usuarios < /docker-entrypoint-initdb.d/schema.sql && \
    echo '☕ Iniciando aplicación Spring Boot...' && \
    java -Xmx512m -Xms256m -Dserver.port=8080 -jar /app/app.jar"