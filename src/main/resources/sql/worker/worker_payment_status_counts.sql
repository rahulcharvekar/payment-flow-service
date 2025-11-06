SELECT status,
       COUNT(*) AS count
FROM worker_payments
WHERE file_id = ?
GROUP BY status
