SELECT
    COUNT(*) AS total_receipts,
    SUM(total_amount) AS total_amount_sum,
    SUM(validated_amount) AS validated_amount_sum,
    AVG(total_amount) AS average_total_amount,
    AVG(validated_amount) AS average_validated_amount,
    COUNT(DISTINCT status) AS unique_statuses,
    COUNT(DISTINCT validated_by) AS unique_validators
FROM employer_payment_receipts
WHERE validated_at BETWEEN ? AND ?
