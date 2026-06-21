-- Apple ile giriş desteği. Google ile aynı desen: users.apple_id (Apple 'sub')
-- + unique. Apple-only hesaplarda password zaten null olabilir (V5'te NOT NULL
-- kaldırılmıştı).
ALTER TABLE users ADD COLUMN apple_id VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uq_users_apple_id UNIQUE (apple_id);
