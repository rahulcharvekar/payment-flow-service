SELECT br.id,
       br.board_id,
       br.board_reference,
       br.employer_reference,
       br.employer_id,
       br.toli_id,
       br.amount,
       br.utr_number,
       br.status,
       br.maker,
       br.checker,
       br.receipt_date
FROM board_receipts br
