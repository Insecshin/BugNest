ALTER TABLE public.app_users
    RENAME COLUMN username TO nickname; -- 将 username 列重命名为 nickname

UPDATE public.app_users
SET nickname = COALESCE(NULLIF(btrim(nickname), ''), user_name);

ALTER TABLE public.app_users
    ALTER COLUMN nickname SET NOT NULL;

ALTER TABLE public.app_users
    DROP CONSTRAINT ck_app_users_username_not_blank;

ALTER TABLE public.app_users
    ADD CONSTRAINT ck_app_users_nickname_not_blank
    CHECK (length(btrim(nickname)) > 0);
