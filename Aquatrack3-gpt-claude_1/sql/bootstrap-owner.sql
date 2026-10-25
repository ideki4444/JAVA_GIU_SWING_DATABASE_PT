-- Run once in an empty users table, in the SAME SQL session as your SET statements.
-- Generate the hash with aquatrack.util.PasswordUtil; never put a plaintext password here.
INSERT INTO users (username, password_hash, full_name, role, is_active)
SELECT @owner_username, @owner_password_hash, @owner_full_name, 'Owner/Admin', 1
WHERE NOT EXISTS (SELECT 1 FROM users)
  AND @owner_username REGEXP '^[A-Za-z0-9_]{3,50}$'
  AND CHAR_LENGTH(TRIM(@owner_full_name)) BETWEEN 1 AND 100
  AND @owner_password_hash REGEXP '^pbkdf2_sha256[$]65536[$][A-Za-z0-9+/=]+[$][A-Za-z0-9+/=]+$';
SELECT IF(ROW_COUNT() = 1, 'Owner account created', 'REFUSED: users exist or setup values are invalid') AS bootstrap_result;
