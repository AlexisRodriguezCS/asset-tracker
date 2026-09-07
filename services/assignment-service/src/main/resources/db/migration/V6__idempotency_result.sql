-- Transfer and offboarding get idempotency keys too, and offboarding's answer does not fit
-- in assignment_id: a sweep returns three lists of asset ids, not one assignment.
--
-- Rather than give each operation its own table, the record carries the answer it has. A
-- check-out or transfer still replays through assignment_id; an offboarding replays this
-- column. Storing the response rather than recomputing it matters here - a repeat sweep
-- would find the assets already back and honestly report "0 collected", which is not the
-- answer the first call gave and not what the caller is retrying for.
ALTER TABLE idempotency_keys ADD COLUMN result_json TEXT;
