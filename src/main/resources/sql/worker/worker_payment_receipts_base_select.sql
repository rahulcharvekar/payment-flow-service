SELECT wpr.id,
       wpr.receipt_number,
       wpr.employer_id,
       wpr.toli_id,
       wpr.created_at,
       wpr.total_records,
       wpr.total_amount,
       wpr.status
FROM worker_payment_receipts wpr
