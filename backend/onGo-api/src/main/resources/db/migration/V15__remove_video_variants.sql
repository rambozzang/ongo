DROP TABLE IF EXISTS video_variants;
DELETE FROM video_processing_progress WHERE stage = 'TRANSCODE';
