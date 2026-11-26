-- Existing gold_price rows use UTC date and minute. Shift both to Asia/Shanghai.
CREATE TABLE gold_price_beijing_stage LIKE gold_price;

INSERT INTO gold_price_beijing_stage (`date`, `minute`, price)
SELECT DATE_ADD(`date`, INTERVAL IF(`minute` >= 960, 1, 0) DAY),
       MOD(`minute` + 480, 1440), price
FROM gold_price;

DELETE FROM gold_price;

INSERT INTO gold_price (`date`, `minute`, price)
SELECT `date`, `minute`, price FROM gold_price_beijing_stage;

DROP TABLE gold_price_beijing_stage;
