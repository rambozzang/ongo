-- ============================================================================
-- onGo - Creator Multi-Platform Management SaaS
-- Flyway Migration: V2__fix_enums.sql
-- Fix ENUM type mismatches between schema and application code
-- ============================================================================

-- video_status: Add PROCESSING, REVIEW, REJECTED
-- (Application uses these statuses but they were missing from the ENUM)
ALTER TYPE video_status ADD VALUE IF NOT EXISTS 'PROCESSING';
ALTER TYPE video_status ADD VALUE IF NOT EXISTS 'REVIEW';
ALTER TYPE video_status ADD VALUE IF NOT EXISTS 'REJECTED';

-- upload_status: Add DRAFT
-- (Videos start as DRAFT before upload begins)
ALTER TYPE upload_status ADD VALUE IF NOT EXISTS 'DRAFT';

-- credit_tx_type: Add REFUND
-- (Needed for credit refund transactions)
ALTER TYPE credit_tx_type ADD VALUE IF NOT EXISTS 'REFUND';

-- schedule_status: Add PROCESSING
-- (Used during schedule execution to prevent concurrent processing)
ALTER TYPE schedule_status ADD VALUE IF NOT EXISTS 'PROCESSING';
