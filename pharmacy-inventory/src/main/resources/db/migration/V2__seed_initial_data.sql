-- =============================================================================
-- Migration V2: Baseline Seed Data (Initial Medicines & Batches)
-- =============================================================================

-- Seed initial medicines using ANSI SQL compatible across PostgreSQL and H2
INSERT INTO medicines (name, description, category, low_stock_threshold, active, created_at, updated_at)
SELECT 'Paracetamol', 'Analgesic and antipyretic for pain and fever', 'Pain Relief', 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name = 'Paracetamol');

INSERT INTO medicines (name, description, category, low_stock_threshold, active, created_at, updated_at)
SELECT 'Amoxicillin', 'Broad-spectrum beta-lactam antibiotic', 'Antibiotics', 15, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name = 'Amoxicillin');

INSERT INTO medicines (name, description, category, low_stock_threshold, active, created_at, updated_at)
SELECT 'Ibuprofen', 'Non-steroidal anti-inflammatory drug (NSAID)', 'Pain Relief', 25, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name = 'Ibuprofen');

INSERT INTO medicines (name, description, category, low_stock_threshold, active, created_at, updated_at)
SELECT 'Cetirizine', 'Second-generation antihistamine for allergy symptoms', 'Allergy', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name = 'Cetirizine');

-- Seed initial batches with varying expiry dates to demonstrate FEFO
INSERT INTO medicine_batches (batch_number, quantity, expiry_date, cost_price, selling_price, active, medicine_id)
SELECT 'PARA-BATCH-001', 50, DATE '2026-06-30', 2.00, 5.00, TRUE, id FROM medicines WHERE name = 'Paracetamol'
AND NOT EXISTS (SELECT 1 FROM medicine_batches WHERE batch_number = 'PARA-BATCH-001');

INSERT INTO medicine_batches (batch_number, quantity, expiry_date, cost_price, selling_price, active, medicine_id)
SELECT 'PARA-BATCH-002', 100, DATE '2027-12-31', 2.20, 5.00, TRUE, id FROM medicines WHERE name = 'Paracetamol'
AND NOT EXISTS (SELECT 1 FROM medicine_batches WHERE batch_number = 'PARA-BATCH-002');

INSERT INTO medicine_batches (batch_number, quantity, expiry_date, cost_price, selling_price, active, medicine_id)
SELECT 'AMOX-BATCH-001', 30, DATE '2026-04-15', 8.50, 15.00, TRUE, id FROM medicines WHERE name = 'Amoxicillin'
AND NOT EXISTS (SELECT 1 FROM medicine_batches WHERE batch_number = 'AMOX-BATCH-001');

INSERT INTO medicine_batches (batch_number, quantity, expiry_date, cost_price, selling_price, active, medicine_id)
SELECT 'IBU-BATCH-001', 150, DATE '2028-01-31', 3.00, 7.50, TRUE, id FROM medicines WHERE name = 'Ibuprofen'
AND NOT EXISTS (SELECT 1 FROM medicine_batches WHERE batch_number = 'IBU-BATCH-001');
