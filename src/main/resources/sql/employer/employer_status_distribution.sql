SELECT status,
       COUNT(*) AS count
FROM employer_payment_receipts
GROUP BY status
ORDER BY count DESC
