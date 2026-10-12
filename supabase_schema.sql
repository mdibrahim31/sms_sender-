-- ==============================================================================
-- Supabase Schema for SMS Forwarder App
-- Table: sms_forwarder
-- ==============================================================================

-- 1. Create table `sms_forwarder`
CREATE TABLE IF NOT EXISTS public.sms_forwarder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    txn_id TEXT DEFAULT NULL,
    phone TEXT NOT NULL,
    full_msg TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 2. Indexes for fast lookup by phone, txn_id, and user_id
CREATE INDEX IF NOT EXISTS idx_sms_forwarder_phone ON public.sms_forwarder (phone);
CREATE INDEX IF NOT EXISTS idx_sms_forwarder_txn_id ON public.sms_forwarder (txn_id);
CREATE INDEX IF NOT EXISTS idx_sms_forwarder_user_id ON public.sms_forwarder (user_id);
CREATE INDEX IF NOT EXISTS idx_sms_forwarder_created_at ON public.sms_forwarder (created_at DESC);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.sms_forwarder ENABLE ROW LEVEL SECURITY;

-- 4. Policies: Allow anon/authenticated roles to SELECT and INSERT
-- (Permits app using Supabase Anon Key to insert forwarded SMS and check existing messages)
CREATE POLICY "Allow anon insert to sms_forwarder"
    ON public.sms_forwarder
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (true);

CREATE POLICY "Allow anon select from sms_forwarder"
    ON public.sms_forwarder
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- 5. Comments on table and columns for documentation
COMMENT ON TABLE public.sms_forwarder IS 'Stores incoming SMS forwarded automatically from Android device';
COMMENT ON COLUMN public.sms_forwarder.id IS 'Unique identifier for each SMS record';
COMMENT ON COLUMN public.sms_forwarder.user_id IS 'Vendor user ID for attributing payments';
COMMENT ON COLUMN public.sms_forwarder.txn_id IS 'Extracted transaction ID from SMS body (e.g. TrxID / TxnId)';
COMMENT ON COLUMN public.sms_forwarder.phone IS 'Sender phone number or alphanumeric title (e.g. bKash, NAGAD)';
COMMENT ON COLUMN public.sms_forwarder.full_msg IS 'Full raw SMS body';
COMMENT ON COLUMN public.sms_forwarder.created_at IS 'Timestamp when the message was forwarded to Supabase';
