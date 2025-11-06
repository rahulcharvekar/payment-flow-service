SELECT
    COUNT(*) AS total_payments,
    SUM(payment_amount) AS total_amount,
    AVG(payment_amount) AS average_amount,
    MIN(payment_amount) AS min_amount,
    MAX(payment_amount) AS max_amount,
    COUNT(DISTINCT status) AS status_count
FROM worker_payments
WHERE file_id = ?
