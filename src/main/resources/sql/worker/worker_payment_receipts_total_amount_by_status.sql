SELECT COALESCE(SUM(total_amount), 0)
FROM worker_payment_receipts
WHERE status = :status
