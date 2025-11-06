SELECT COALESCE(SUM(amount), 0)
FROM board_receipts
WHERE board_id = ?
