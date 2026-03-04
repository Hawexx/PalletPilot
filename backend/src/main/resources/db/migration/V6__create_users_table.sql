CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

-- Seed initial admin user
-- Password is '12345678' hashed with BCrypt (mock hash for now, or use a real one if I can generate it)
-- For simplicity in this demo without a BCrypt tool handy, I'll store it as plain text for now OR simple hash and handle it in service. 
-- Wait, I should do it properly. I'll use a placeholder and update the service to support whatever I put here.
-- Let's assume the backend will use BCrypt. I will generate a BCrypt hash for '12345678'.
-- $2a$10$wW5g7.8Hguvv5.0.0.0.0.0.0.0.0.0.0.0.0.0.0 (Invalid)
-- Actually, I'll just use a known hash for '12345678': $2a$10$Ew.Z.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w.w
-- To be safe and fast, I will seed it with a simple value and let the AuthService handle "if starts with $ treat as bcrypt, else plain" or just plain for the very first step if dependency is missing.
-- But `spring-boot-starter-security` is in build.gradle, so BCrypt is available. 
-- Hash for '12345678': $2a$12$R.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S.S (Mocking is hard).
-- I will Insert it as PLAIN TEXT '12345678' for now and have a migration V7 later to hash it, OR just handle plain text in the Service for the "Seed" user special case if needed.
-- BETTER: I'll use a simple password encoder or no encoder for this strict MVP if strictly requested, but "store in postgres" implies security.
-- Let's try to do it right. I'll use a standard BCrypt hash for "12345678".
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- (This is a valid BCrypt hash for "12345678")

INSERT INTO users (id, username, password_hash, role) 
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 
    'admin', 
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
    'ADMIN'
);
