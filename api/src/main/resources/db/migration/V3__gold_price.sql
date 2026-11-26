CREATE TABLE gold_price (
    `date` DATE NOT NULL,
    `minute` INT NOT NULL,
    price DOUBLE NOT NULL,
    UNIQUE KEY uk_gold_price_date_minute (`date`, `minute`),
    CONSTRAINT chk_gold_price_minute CHECK (`minute` BETWEEN 0 AND 1439),
    CONSTRAINT chk_gold_price_value CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
