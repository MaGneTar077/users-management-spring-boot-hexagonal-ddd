-- =============================================
-- Script de creación de la base de datos
-- Gestión de Usuarios - Arquitectura Hexagonal
-- (PostgreSQL)
-- =============================================

-- En Render la base de datos ya viene creada por el servicio gestionado,
-- por lo que no se necesita CREATE DATABASE / USE aquí.

CREATE TABLE IF NOT EXISTS users (
                                     id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL
    CHECK (role IN ('ADMIN', 'MEMBER', 'REVIEWER')),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'BLOCKED')),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- PostgreSQL no soporta "ON UPDATE CURRENT_TIMESTAMP" en la definición de columna.
-- Se emula con una función + trigger que actualiza updated_at en cada UPDATE.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Usuario administrador inicial (password: Admin1234!)
INSERT INTO users (id, name, email, password, role, status)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'Administrador',
           'admin@example.com',
           '$2a$12$placeholderHashReplaceWithRealBCryptHash',
           'ADMIN',
           'ACTIVE'
       )
    ON CONFLICT (email) DO NOTHING;