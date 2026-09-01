#!/bin/bash
set -e

echo "========================================="
echo "🚀 Iniciando contenedor con MySQL + App"
echo "========================================="

# Iniciar MySQL con el comando correcto
echo "📦 Iniciando MySQL..."
mysqld_safe --bind-address=0.0.0.0 --datadir=/var/lib/mysql &

# Esperar a que MySQL esté listo
echo "⏳ Esperando a que MySQL esté listo..."
sleep 15

# Verificar que MySQL está corriendo
if mysqladmin ping -h localhost -u root --silent 2>/dev/null; then
    echo "✅ MySQL está funcionando correctamente"
else
    echo "❌ Error: MySQL no se inició correctamente"
    exit 1
fi

# Crear la base de datos y ejecutar el schema
echo "📦 Creando base de datos y ejecutando schema..."
mysql -u root <<-EOSQL
    CREATE DATABASE IF NOT EXISTS crud_usuarios;
    USE crud_usuarios;
    SOURCE /docker-entrypoint-initdb.d/schema.sql;
EOSQL

# Iniciar la aplicación Spring Boot
echo "========================================="
echo "☕ Iniciando aplicación Spring Boot..."
echo "========================================="
exec java -jar /app/app.jar