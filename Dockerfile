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
    mkdir -p /var/lib/mysql && \
    chown -R mysql:mysql /var/run/mysqld && \
    chown -R mysql:mysql /var/lib/mysql && \
    chmod -R 755 /var/run/mysqld && \
    chmod -R 755 /var/lib/mysql

# Copiar schema
RUN mkdir -p /docker-entrypoint-initdb.d/
COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/

# Copiar aplicación
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Iniciar MySQL y Spring Boot con logs COMPLETOS
CMD bash -c "mysqld_safe --bind-address=0.0.0.0 --datadir=/var/lib/mysql & \
    echo '⏳ Esperando a que MySQL esté listo...' && \
    sleep 15 && \
    echo '✅ MySQL iniciado' && \
    mysql -u root -e 'CREATE DATABASE IF NOT EXISTS crud_usuarios;' && \
    mysql -u root crud_usuarios < /docker-entrypoint-initdb.d/schema.sql && \
    echo '=========================================' && \
    echo '☕ Iniciando Spring Boot con logs COMPLETOS' && \
    echo '=========================================' && \
    java -Xmx512m -Xms256m -Dserver.port=8080 \
         -Dlogging.level.root=DEBUG \
         -Dlogging.level.org.springframework=DEBUG \
         -Dlogging.level.com.zaxxer.hikari=TRACE \
         -jar /app/app.jar"