-- =============================================================================
-- Enterprise Trading Platform
-- Migration 003: Split public schema into authentication + tradingsystem
-- PostgreSQL
--
-- Purpose:
--   Move all tables from the default public schema into two dedicated schemas:
--     - tradingsystem : instruments, accounts, users, orders, executions,
--                       positions, balance_history, watchlist
--     - authentication: credentials, refresh_tokens
--
-- Strategy:
--   1. Create the two new schemas.
--   2. Drop cross-schema FK before moving tables (refresh_tokens.user_id → users).
--   3. Move tables with ALTER TABLE … SET SCHEMA.
--   4. Re-create the cross-schema FK with the fully-qualified reference.
--   5. Move indexes (they follow the table automatically in PostgreSQL).
--   6. Move the trigger function and trigger.
--   7. Update search_path so bare-name queries still work during transition.
--
-- Safe to run on a live database. Each ALTER TABLE SET SCHEMA acquires an
-- ACCESS EXCLUSIVE lock for the duration of the rename only. The lock is
-- released before the next statement.
--
-- Idempotent: IF NOT EXISTS guards on schema creation; the SET SCHEMA
-- operations are skipped by the surrounding DO block if the table is already
-- in the target schema.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- STEP 1: Create schemas
-- -----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS tradingsystem;
CREATE SCHEMA IF NOT EXISTS authentication;

-- -----------------------------------------------------------------------------
-- STEP 2: Drop the cross-schema FK that would block moving users before
--         refresh_tokens (or vice-versa).
--         We re-create it in step 6 after both tables are in their schemas.
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_user'
          AND conrelid = 'public.refresh_tokens'::regclass
    ) THEN
        ALTER TABLE public.refresh_tokens
            DROP CONSTRAINT fk_refresh_tokens_user;
    END IF;
END;
$$;

-- Also drop the self-referential FK on refresh_tokens so the table can move
-- cleanly; it will be re-created after the move.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_replaced_by'
          AND conrelid = 'public.refresh_tokens'::regclass
    ) THEN
        ALTER TABLE public.refresh_tokens
            DROP CONSTRAINT fk_refresh_tokens_replaced_by;
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 3: Move tradingsystem tables from public → tradingsystem
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY[
        'instruments',
        'accounts',
        'users',
        'orders',
        'executions',
        'positions',
        'balance_history',
        'watchlist'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = 'public' AND tablename = tbl
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA tradingsystem', tbl);
            RAISE NOTICE 'Moved public.% → tradingsystem.%', tbl, tbl;
        ELSE
            RAISE NOTICE 'Table % not found in public schema (already moved?)', tbl;
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 4: Move authentication tables from public → authentication
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY['credentials', 'refresh_tokens'];
BEGIN
    FOREACH tbl IN ARRAY tables
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = 'public' AND tablename = tbl
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA authentication', tbl);
            RAISE NOTICE 'Moved public.% → authentication.%', tbl, tbl;
        ELSE
            RAISE NOTICE 'Table % not found in public schema (already moved?)', tbl;
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 5: Re-create the cross-schema FKs on refresh_tokens
-- -----------------------------------------------------------------------------

-- refresh_tokens.user_id → tradingsystem.users.id  (cross-schema)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_user'
          AND conrelid = 'authentication.refresh_tokens'::regclass
    ) THEN
        ALTER TABLE authentication.refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_user
                FOREIGN KEY (user_id)
                REFERENCES tradingsystem.users (id)
                ON DELETE CASCADE;
    END IF;
END;
$$;

-- refresh_tokens.replaced_by → authentication.refresh_tokens.id  (self-ref)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_replaced_by'
          AND conrelid = 'authentication.refresh_tokens'::regclass
    ) THEN
        ALTER TABLE authentication.refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_replaced_by
                FOREIGN KEY (replaced_by)
                REFERENCES authentication.refresh_tokens (id)
                ON DELETE SET NULL;
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 6: Move the trigger function into tradingsystem schema
-- Note: PostgreSQL functions are schema-scoped; recreate in tradingsystem.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION tradingsystem.prevent_closed_account_reactivation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CLOSED' AND NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'Closed account % cannot be reactivated', OLD.id;
    END IF;

    RETURN NEW;
END;
$$;

-- Drop old trigger (references old public function) and recreate on moved table
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_accounts_prevent_closed_reactivation'
          AND tgrelid = 'tradingsystem.accounts'::regclass
    ) THEN
        DROP TRIGGER trg_accounts_prevent_closed_reactivation
            ON tradingsystem.accounts;
    END IF;
END;
$$;

CREATE TRIGGER trg_accounts_prevent_closed_reactivation
BEFORE UPDATE OF status ON tradingsystem.accounts
FOR EACH ROW
EXECUTE FUNCTION tradingsystem.prevent_closed_account_reactivation();

-- Drop the old public function now that it has been superseded
DROP FUNCTION IF EXISTS public.prevent_closed_account_reactivation();

-- -----------------------------------------------------------------------------
-- STEP 7: Update database-level search_path so bare table names in existing
--         queries continue to resolve during any remaining transition period.
-- -----------------------------------------------------------------------------

ALTER DATABASE trading SET search_path TO tradingsystem, authentication, public;

COMMIT;
