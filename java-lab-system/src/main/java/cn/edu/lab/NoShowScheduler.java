package cn.edu.lab;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NoShowScheduler {
    private final JdbcTemplate db;
    public NoShowScheduler(JdbcTemplate db) { this.db = db; }

    // A reservation without check-in 30 minutes after its start is recorded as a violation.
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void markNoShows() {
        var due = db.queryForList("SELECT reservation_id,user_id FROM reservations WHERE status='待使用' AND start_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE) FOR UPDATE");
        for (var row : due) {
            int changed = db.update("UPDATE reservations SET status='违约' WHERE reservation_id=? AND status='待使用'", row.get("reservation_id"));
            if (changed == 1) db.update("UPDATE users SET ban_until=CASE WHEN violation_count+1>=3 THEN DATE_ADD(NOW(),INTERVAL 7 DAY) ELSE ban_until END, violation_count=violation_count+1 WHERE user_id=?", row.get("user_id"));
        }
    }
}
