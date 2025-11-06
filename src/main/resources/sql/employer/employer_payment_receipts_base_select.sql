SELECT id,
       employer_receipt_number,
       worker_receipt_number,
       employer_id,
       toli_id,
       transaction_reference,
       validated_by,
       validated_at,
       total_records,
       total_amount,
       status
FROM employer_payment_receipts
