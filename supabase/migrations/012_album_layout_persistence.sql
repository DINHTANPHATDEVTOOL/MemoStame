-- Migration: 012_album_layout_persistence.sql
-- Description: Establishes account-scoped persistent layout authority for 3D Stamp Book (Task #76)

-- 1. Table: public.album_pages
CREATE TABLE IF NOT EXISTS public.album_pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    album_id TEXT NOT NULL,
    page_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT album_pages_owner_album_page_uq UNIQUE (owner_id, album_id, page_index),
    CONSTRAINT album_pages_page_index_check CHECK (page_index >= 0),
    CONSTRAINT album_pages_album_id_check CHECK (length(trim(album_id)) > 0 AND length(album_id) <= 256)
);

-- Indexes on album_pages
CREATE INDEX IF NOT EXISTS idx_album_pages_owner_album ON public.album_pages(owner_id, album_id);

-- 2. Table: public.album_stamp_placements
CREATE TABLE IF NOT EXISTS public.album_stamp_placements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    album_id TEXT NOT NULL,
    page_index INTEGER NOT NULL,
    stamp_id TEXT NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    scale DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    rotation_degrees DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    z_index INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT album_stamp_placements_page_fk FOREIGN KEY (owner_id, album_id, page_index)
        REFERENCES public.album_pages (owner_id, album_id, page_index) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT album_stamp_placements_owner_album_stamp_uq UNIQUE (owner_id, album_id, stamp_id),
    CONSTRAINT placements_page_index_check CHECK (page_index >= 0),
    CONSTRAINT placements_x_check CHECK (x >= 0.0 AND x <= 1.0 AND NOT isnan(x) AND NOT isinf(x)),
    CONSTRAINT placements_y_check CHECK (y >= 0.0 AND y <= 1.0 AND NOT isnan(y) AND NOT isinf(y)),
    CONSTRAINT placements_scale_check CHECK (scale > 0.05 AND scale <= 5.0 AND NOT isnan(scale) AND NOT isinf(scale)),
    CONSTRAINT placements_rotation_check CHECK (rotation_degrees >= -360.0 AND rotation_degrees <= 360.0 AND NOT isnan(rotation_degrees) AND NOT isinf(rotation_degrees)),
    CONSTRAINT placements_z_index_check CHECK (z_index >= -1000 AND z_index <= 1000),
    CONSTRAINT placements_album_id_check CHECK (length(trim(album_id)) > 0 AND length(album_id) <= 256),
    CONSTRAINT placements_stamp_id_check CHECK (length(trim(stamp_id)) > 0 AND length(stamp_id) <= 256)
);

-- Indexes on album_stamp_placements
CREATE INDEX IF NOT EXISTS idx_album_placements_owner_album ON public.album_stamp_placements(owner_id, album_id);
CREATE INDEX IF NOT EXISTS idx_album_placements_page ON public.album_stamp_placements(owner_id, album_id, page_index);

-- 3. Automatic updated_at Trigger
CREATE OR REPLACE FUNCTION public.set_album_layout_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_album_pages_updated_at ON public.album_pages;
CREATE TRIGGER trg_album_pages_updated_at
BEFORE UPDATE ON public.album_pages
FOR EACH ROW
EXECUTE FUNCTION public.set_album_layout_updated_at();

DROP TRIGGER IF EXISTS trg_album_stamp_placements_updated_at ON public.album_stamp_placements;
CREATE TRIGGER trg_album_stamp_placements_updated_at
BEFORE UPDATE ON public.album_stamp_placements
FOR EACH ROW
EXECUTE FUNCTION public.set_album_layout_updated_at();

-- 4. Row Level Security (RLS) & Permissions
ALTER TABLE public.album_pages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.album_stamp_placements ENABLE ROW LEVEL SECURITY;

-- Deny anon access explicitly
REVOKE ALL ON TABLE public.album_pages FROM anon;
REVOKE ALL ON TABLE public.album_stamp_placements FROM anon;

-- Grant permissions to authenticated role
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.album_pages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.album_stamp_placements TO authenticated;

-- RLS Policies for album_pages
DROP POLICY IF EXISTS "Users can view own album pages" ON public.album_pages;
CREATE POLICY "Users can view own album pages"
ON public.album_pages
FOR SELECT
TO authenticated
USING (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can insert own album pages" ON public.album_pages;
CREATE POLICY "Users can insert own album pages"
ON public.album_pages
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can update own album pages" ON public.album_pages;
CREATE POLICY "Users can update own album pages"
ON public.album_pages
FOR UPDATE
TO authenticated
USING (auth.uid() = owner_id)
WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can delete own album pages" ON public.album_pages;
CREATE POLICY "Users can delete own album pages"
ON public.album_pages
FOR DELETE
TO authenticated
USING (auth.uid() = owner_id);

-- RLS Policies for album_stamp_placements
DROP POLICY IF EXISTS "Users can view own placements" ON public.album_stamp_placements;
CREATE POLICY "Users can view own placements"
ON public.album_stamp_placements
FOR SELECT
TO authenticated
USING (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can insert own placements" ON public.album_stamp_placements;
CREATE POLICY "Users can insert own placements"
ON public.album_stamp_placements
FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can update own placements" ON public.album_stamp_placements;
CREATE POLICY "Users can update own placements"
ON public.album_stamp_placements
FOR UPDATE
TO authenticated
USING (auth.uid() = owner_id)
WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Users can delete own placements" ON public.album_stamp_placements;
CREATE POLICY "Users can delete own placements"
ON public.album_stamp_placements
FOR DELETE
TO authenticated
USING (auth.uid() = owner_id);
