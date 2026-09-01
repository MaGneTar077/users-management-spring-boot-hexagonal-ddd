#!/bin/bash
set -e

echo "========================================="
echo "🚀 Iniciando contenedor con MySQL + App"
echo "========================================="

echo "📦 Iniciando MySQL..."
mysqld_safe --bind-address=0.0.0.0 --datadir=/var/lib/mysql &

echo "⏳ Esperando a que MySQL esté listo..."
sleep 15

if mysqladmin ping -h localhost -u root --silent 2>/dev/null; then
    echo "✅ MySQL está funcionando correctamente"
else
    echo "❌ Error: MySQL no se inició correctamente"
    exit 1
fi

echo "📦 Creando base de datos..."
mysql -u root <<-EOSQL
    CREATE DATABASE IF NOT EXISTS crud_usuarios;
    USE crud_usuarios;
    SOURCE /docker-entrypoint-initdb.d/schema.sql;
EOSQL

echo "========================================="
echo "☕ Iniciando aplicación Spring Boot..."
echo "========================================="

java -Xmx512m -Xms256m -Dserver.port=8080 -jar /app/app.jar

echo "❌ ERROR: La aplicación Spring Boot terminó inesperadamente"
exit 1