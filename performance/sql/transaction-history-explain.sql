\set ON_ERROR_STOP on

\echo 'Dataset size'
SELECT count(*) AS total_history_rows,
       count(DISTINCT account_id) AS accounts_with_history
FROM account_transactions;

\echo 'Target account size'
SELECT count(*) AS target_history_rows
FROM account_transactions
WHERE account_id = :'account_id'::uuid;

\echo 'Page 0 data query'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, account_id, transfer_id, transaction_type, amount,
       balance_before, balance_after, created_at
FROM account_transactions
WHERE account_id = :'account_id'::uuid
ORDER BY created_at DESC, id DESC
LIMIT :page_size OFFSET 0;

\echo 'Spring Data Page count query'
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(id)
FROM account_transactions
WHERE account_id = :'account_id'::uuid;

\echo 'Deep offset data query'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, account_id, transfer_id, transaction_type, amount,
       balance_before, balance_after, created_at
FROM account_transactions
WHERE account_id = :'account_id'::uuid
ORDER BY created_at DESC, id DESC
LIMIT :page_size OFFSET :deep_offset;
