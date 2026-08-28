-- Migration 004: Enable Realtime Publication for Direct Messages
-- Idempotent and safe migration for public.direct_messages table

-- 1. Ensure table has REPLICA IDENTITY FULL for complete Realtime payload detail (including UPDATE / DELETE events)
ALTER TABLE public.direct_messages REPLICA IDENTITY FULL;

-- 2. Add public.direct_messages to publication 'supabase_realtime' if the publication exists and table is not already added
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_rel pr
      JOIN pg_publication p ON p.oid = pr.prpubid
      JOIN pg_class c ON c.oid = pr.prrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE p.pubname = 'supabase_realtime'
        AND n.nspname = 'public'
        AND c.relname = 'direct_messages'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.direct_messages;
    END IF;
  END IF;
END $$;
