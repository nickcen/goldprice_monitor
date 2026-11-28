package com.goldpricemonitor.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Converts credentials saved before encryption was introduced, before serving requests. */
@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class GoldCredentialMigration implements ApplicationRunner {
    private final JdbcTemplate db;
    private final GoldCredentialCipher cipher;

    public GoldCredentialMigration(JdbcTemplate db, @Value("${app.system-salt:}") String systemSalt) {
        this.db = db;
        this.cipher = new GoldCredentialCipher(systemSalt);
    }

    @Override public void run(ApplicationArguments arguments) {
        db.query("SELECT id,salt,password,payment_password FROM gold_account", rs -> {
            int id = rs.getInt("id");
            String salt = rs.getString("salt");
            String password = rs.getString("password");
            String payment = rs.getString("payment_password");
            if (salt == null) salt = GoldCredentialCipher.newSalt();
            boolean change = rs.getString("salt") == null;
            if (password != null && !password.startsWith("v1:")) { password = cipher.encrypt(password, salt); change = true; }
            if (payment != null && !payment.startsWith("v1:")) { payment = cipher.encrypt(payment, salt); change = true; }
            if (change) db.update("UPDATE gold_account SET salt=?,password=?,payment_password=? WHERE id=?", salt, password, payment, id);
        });
    }
}
