ALTER TABLE gold_account ADD COLUMN salt VARCHAR(12) NULL COMMENT 'Gold 账号独立加密盐';
ALTER TABLE gold_account MODIFY COLUMN password TEXT NULL COMMENT 'Gold 服务账号加密密码';
ALTER TABLE gold_account MODIFY COLUMN payment_password TEXT NULL COMMENT 'Gold 加密支付密码';
