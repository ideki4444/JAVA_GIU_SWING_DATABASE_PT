-- First application account only. Run schema.sql first, then set these variables
-- in the SAME mysql/phpMyAdmin SQL session before running this file:
--   SET @owner_username = 'your_chosen_username';
--   SET @owner_full_name = 'Your Name';
--   SET @owner_password_hash = 'paste_the_complete_PasswordUtil_output_here';
-- Generate a hash using aquatrack.util.PasswordUtil, not SQL SHA2/MD5 functions.
-- Do not put the plaintext password here. There is no default login/password.
-- For phpMyAdmin, submit the SET statements and this file in one SQL execution.

USE aquatrack;

-- Require the exact PBKDF2 format emitted by PasswordUtil (16-byte salt and
-- 32-byte derived key). Unset variables and unedited placeholders fail closed.
SET @aquatrack_owner_valid = COALESCE(
    CHAR_LENGTH(@owner_username) BETWEEN 3 AND 50
    AND @owner_username NOT REGEXP '[^A-Za-z0-9_]'
    AND CHAR_LENGTH(TRIM(@owner_full_name)) BETWEEN 1 AND 100
    AND CHAR_LENGTH(@owner_password_hash) = 89
    AND CONVERT(@owner_password_hash USING utf8mb4) COLLATE utf8mb4_bin REGEXP
        '^pbkdf2_sha256[$]65536[$][A-Za-z0-9+/]{22}==[$][A-Za-z0-9+/]{43}=$',
    0
);
SET @aquatrack_users_empty = (SELECT COUNT(*) = 0 FROM users);

INSERT INTO users (username, password_hash, full_name, role, is_active)
SELECT @owner_username, @owner_password_hash, TRIM(@owner_full_name), 'Owner/Admin', 1
WHERE @aquatrack_owner_valid = 1 AND @aquatrack_users_empty = 1;

SET @aquatrack_owner_created = ROW_COUNT();
SELECT CASE
    WHEN @aquatrack_owner_created = 1 THEN 'Owner account created. Sign in with your chosen username and password.'
    WHEN @aquatrack_users_empty = 0 THEN 'REFUSED: users already exist. No accounts were changed; use Accounts to manage logins.'
    ELSE 'REFUSED: set a valid username, full name, and complete PasswordUtil hash in this same SQL session.'
END AS bootstrap_result;

SET @owner_password_hash = NULL;
SET @aquatrack_owner_valid = NULL;
SET @aquatrack_users_empty = NULL;
SET @aquatrack_owner_created = NULL;
