CREATE TABLE public.app_users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_name VARCHAR(30) NOT NULL,
    username VARCHAR(50),
    email VARCHAR(254) NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_users_user_name UNIQUE (user_name),
    CONSTRAINT ck_app_users_user_name_format
        CHECK (user_name ~ '^[a-z0-9_]+$'),
    CONSTRAINT ck_app_users_email_not_blank
        CHECK (length(btrim(email)) > 0),
    CONSTRAINT ck_app_users_username_not_blank
        CHECK (username IS NULL OR length(btrim(username)) > 0)
);

CREATE UNIQUE INDEX uq_app_users_email_ci
    ON public.app_users (lower(btrim(email)));
