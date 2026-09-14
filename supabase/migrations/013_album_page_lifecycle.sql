-- Migration: 013_album_page_lifecycle.sql
-- Description: Establishes cloud-authoritative album page lifecycle, stable page identity, empty-page persistence, and atomic reorder/safe-remove RPCs (Task #80)

-- 1. Schema Evolution: Add page_id to album_stamp_placements for stable page identity
ALTER TABLE public.album_stamp_placements ADD COLUMN IF NOT EXISTS page_id UUID;

-- Backfill existing page_id from album_pages
UPDATE public.album_stamp_placements p
SET page_id = ap.id
FROM public.album_pages ap
WHERE p.owner_id = ap.owner_id
  AND p.album_id = ap.album_id
  AND p.page_index = ap.page_index
  AND p.page_id IS NULL;

-- Foreign key on page_id with CASCADE delete
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'album_stamp_placements_page_id_fk'
    ) THEN
        ALTER TABLE public.album_stamp_placements
        ADD CONSTRAINT album_stamp_placements_page_id_fk
        FOREIGN KEY (page_id) REFERENCES public.album_pages(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_album_placements_page_id ON public.album_stamp_placements(page_id);

-- 2. Trigger: Auto-populate page_id on placement insert if omitted (backward compatibility)
CREATE OR REPLACE FUNCTION public.set_album_placement_page_id()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.page_id IS NULL THEN
        SELECT id INTO NEW.page_id
        FROM public.album_pages
        WHERE owner_id = NEW.owner_id
          AND album_id = NEW.album_id
          AND page_index = NEW.page_index
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_set_album_placement_page_id ON public.album_stamp_placements;
CREATE TRIGGER trg_set_album_placement_page_id
BEFORE INSERT ON public.album_stamp_placements
FOR EACH ROW
EXECUTE FUNCTION public.set_album_placement_page_id();

-- 3. Atomic Reorder RPC: reorder_album_pages
CREATE OR REPLACE FUNCTION public.reorder_album_pages(
    p_album_id TEXT,
    p_page_ids UUID[]
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_owner_id UUID;
    v_actual_count INTEGER;
    v_input_count INTEGER;
    v_page_id UUID;
    v_idx INTEGER;
BEGIN
    -- Derive auth.uid() internally
    v_owner_id := auth.uid();
    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;

    -- Validate input album_id
    IF p_album_id IS NULL OR length(trim(p_album_id)) = 0 OR length(p_album_id) > 256 THEN
        RAISE EXCEPTION 'Invalid album_id' USING ERRCODE = '22023';
    END IF;

    -- Virtual albums cannot be modified
    IF p_album_id LIKE 'loc_%' OR p_album_id LIKE 'virtual_%' THEN
        RAISE EXCEPTION 'Virtual albums cannot be modified' USING ERRCODE = '42501';
    END IF;

    -- Validate input array
    v_input_count := array_length(p_page_ids, 1);
    IF v_input_count IS NULL OR v_input_count = 0 THEN
        RAISE EXCEPTION 'Page IDs array cannot be empty' USING ERRCODE = '22023';
    END IF;

    -- Lock Album page rows for this owner and album
    PERFORM id
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id
    FOR UPDATE;

    -- Verify that input array count matches existing pages count
    SELECT COUNT(*) INTO v_actual_count
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id;

    IF v_actual_count != v_input_count THEN
        RAISE EXCEPTION 'Input page count % does not match album page count %', v_input_count, v_actual_count
            USING ERRCODE = '22023';
    END IF;

    -- Verify all input IDs belong to this album and owner
    SELECT COUNT(*) INTO v_actual_count
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id AND id = ANY(p_page_ids);

    IF v_actual_count != v_input_count THEN
        RAISE EXCEPTION 'Input contains invalid, foreign, or duplicate page IDs' USING ERRCODE = '22023';
    END IF;

    -- Temporary shift to avoid unique constraint (owner_id, album_id, page_index) collision
    -- Note: ON UPDATE CASCADE on placements FK updates placement.page_index concurrently
    UPDATE public.album_pages
    SET page_index = page_index + 100000
    WHERE owner_id = v_owner_id AND album_id = p_album_id;

    -- Set final 0-based page_index in the specified order
    FOR v_idx IN 1..v_input_count LOOP
        v_page_id := p_page_ids[v_idx];
        UPDATE public.album_pages
        SET page_index = v_idx - 1,
            updated_at = now()
        WHERE id = v_page_id AND owner_id = v_owner_id AND album_id = p_album_id;
    END LOOP;

    RETURN jsonb_build_object('success', true, 'reordered_count', v_input_count);
END;
$$;

-- 4. Safe Remove Page RPC: remove_album_page
CREATE OR REPLACE FUNCTION public.remove_album_page(
    p_album_id TEXT,
    p_page_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_owner_id UUID;
    v_page_index INTEGER;
    v_total_pages INTEGER;
    v_placements_count INTEGER;
BEGIN
    -- Derive auth.uid() internally
    v_owner_id := auth.uid();
    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;

    -- Validate input
    IF p_album_id IS NULL OR length(trim(p_album_id)) = 0 OR length(p_album_id) > 256 THEN
        RAISE EXCEPTION 'Invalid album_id' USING ERRCODE = '22023';
    END IF;

    IF p_album_id LIKE 'loc_%' OR p_album_id LIKE 'virtual_%' THEN
        RAISE EXCEPTION 'Virtual albums cannot be modified' USING ERRCODE = '42501';
    END IF;

    -- Lock Album pages
    PERFORM id
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id
    FOR UPDATE;

    -- Verify target page exists and obtain page_index
    SELECT page_index INTO v_page_index
    FROM public.album_pages
    WHERE id = p_page_id AND owner_id = v_owner_id AND album_id = p_album_id;

    IF v_page_index IS NULL THEN
        RAISE EXCEPTION 'Page not found' USING ERRCODE = '22023';
    END IF;

    -- Total pages check: cannot delete the only page
    SELECT COUNT(*) INTO v_total_pages
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id;

    IF v_total_pages <= 1 THEN
        RAISE EXCEPTION 'Cannot remove the only page of an album' USING ERRCODE = '22023';
    END IF;

    -- Invariant: Non-empty page cannot be deleted
    SELECT COUNT(*) INTO v_placements_count
    FROM public.album_stamp_placements
    WHERE owner_id = v_owner_id
      AND album_id = p_album_id
      AND (page_id = p_page_id OR page_index = v_page_index);

    IF v_placements_count > 0 THEN
        RAISE EXCEPTION 'Page contains % stamp placement(s) and cannot be removed', v_placements_count
            USING ERRCODE = '22023';
    END IF;

    -- Delete the page
    DELETE FROM public.album_pages
    WHERE id = p_page_id AND owner_id = v_owner_id AND album_id = p_album_id;

    -- Reindex remaining pages that had a higher page_index
    UPDATE public.album_pages
    SET page_index = page_index + 100000
    WHERE owner_id = v_owner_id AND album_id = p_album_id AND page_index > v_page_index;

    UPDATE public.album_pages
    SET page_index = (page_index - 100000) - 1,
        updated_at = now()
    WHERE owner_id = v_owner_id AND album_id = p_album_id AND page_index >= 100000;

    RETURN jsonb_build_object('success', true, 'removed_page_id', p_page_id);
END;
$$;

-- 5. Append Page RPC: append_album_page
CREATE OR REPLACE FUNCTION public.append_album_page(
    p_album_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_owner_id UUID;
    v_total_pages INTEGER;
    v_next_index INTEGER;
    v_new_page_id UUID;
BEGIN
    -- Derive auth.uid() internally
    v_owner_id := auth.uid();
    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;

    IF p_album_id IS NULL OR length(trim(p_album_id)) = 0 OR length(p_album_id) > 256 THEN
        RAISE EXCEPTION 'Invalid album_id' USING ERRCODE = '22023';
    END IF;

    IF p_album_id LIKE 'loc_%' OR p_album_id LIKE 'virtual_%' THEN
        RAISE EXCEPTION 'Virtual albums cannot be modified' USING ERRCODE = '42501';
    END IF;

    -- Lock pages
    PERFORM id
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id
    FOR UPDATE;

    -- Check maximum pages limit (50 pages per album)
    SELECT COUNT(*) INTO v_total_pages
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id;

    IF v_total_pages >= 50 THEN
        RAISE EXCEPTION 'Maximum pages limit reached (50)' USING ERRCODE = '22023';
    END IF;

    -- Determine next page_index
    SELECT COALESCE(MAX(page_index), -1) + 1 INTO v_next_index
    FROM public.album_pages
    WHERE owner_id = v_owner_id AND album_id = p_album_id;

    v_new_page_id := gen_random_uuid();

    INSERT INTO public.album_pages (id, owner_id, album_id, page_index, created_at, updated_at)
    VALUES (v_new_page_id, v_owner_id, p_album_id, v_next_index, now(), now());

    RETURN jsonb_build_object(
        'success', true,
        'id', v_new_page_id,
        'owner_id', v_owner_id,
        'album_id', p_album_id,
        'page_index', v_next_index
    );
END;
$$;

-- 6. Permissions
REVOKE ALL ON FUNCTION public.reorder_album_pages(TEXT, UUID[]) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.reorder_album_pages(TEXT, UUID[]) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.remove_album_page(TEXT, UUID) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.remove_album_page(TEXT, UUID) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.append_album_page(TEXT) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.append_album_page(TEXT) TO authenticated, service_role;
