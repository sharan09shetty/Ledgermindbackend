-- Tracks whether the user has completed the first-login onboarding flow.
-- Defaults to FALSE so every account (including pre-existing ones) is walked
-- through onboarding exactly once.
ALTER TABLE users
    ADD COLUMN onboarded BOOLEAN NOT NULL DEFAULT FALSE;
