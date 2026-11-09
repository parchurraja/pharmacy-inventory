-- V3: Add historical sale-time cost price to sale items
ALTER TABLE sale_items
ADD COLUMN cost_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00;
