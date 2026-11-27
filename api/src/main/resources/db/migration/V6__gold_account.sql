CREATE TABLE gold_account (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    username VARCHAR(120) NOT NULL,
    password VARCHAR(255) NULL COMMENT 'Gold 服务账号明文密码',
    token TEXT NULL,
    is_online TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_gold_account_user (user_id, id),
    CONSTRAINT fk_gold_account_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT chk_gold_account_online CHECK (is_online IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
