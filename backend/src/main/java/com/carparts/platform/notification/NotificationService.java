package com.carparts.platform.notification;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    public NotificationService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record NotificationRow(UUID id,String audience,String channel,String status,String kind,String title,String body,
                                  String link,UUID caseId,UUID productId,Instant readAt,Instant createdAt){}

    @Transactional
    public int notifyRoles(Set<String> roleCodes,String kind,String title,String body,String link,
                           UUID caseId,UUID productId,UUID actorId) {
        if(roleCodes==null||roleCodes.isEmpty()) return 0;
        String placeholders=String.join(",",Collections.nCopies(roleCodes.size(),"?"));
        String sql="""
                INSERT INTO notifications(recipient_user_id,audience,channel,status,kind,title,body,link,case_id,product_id,created_by_user_id)
                SELECT DISTINCT ur.user_id,'STAFF','IN_APP','UNREAD',?,?,?,?,?,?,?
                FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN app_users u ON u.id=ur.user_id
                WHERE u.status='ACTIVE' AND r.code IN (%s)
                """.formatted(placeholders);
        List<Object> args=new ArrayList<>();
        args.add(kind);args.add(title);args.add(body);args.add(link);args.add(caseId);args.add(productId);args.add(actorId);
        args.addAll(roleCodes);
        return jdbc.update(sql,args.toArray());
    }

    @Transactional
    public int notifyCustomerCase(UUID caseId,String kind,String title,String body,String link,String preferredChannel,AuthPrincipal actor) {
        Map<String,Object> target;
        try {
            target=jdbc.queryForMap("""
                    SELECT c.user_id,r.contact_phone,r.contact_email
                    FROM workflow_cases w
                    JOIN customer_requests r ON r.id=w.request_id
                    LEFT JOIN customers c ON c.id=r.customer_id
                    WHERE w.id=?
                    """,caseId);
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"پرونده پیدا نشد."); }
        UUID userId=(UUID)target.get("user_id");
        String phone=(String)target.get("contact_phone"), email=(String)target.get("contact_email");
        UUID productId=jdbc.queryForObject("SELECT product_id FROM workflow_cases WHERE id=?",UUID.class,caseId);
        if(userId!=null) {
            return jdbc.update("""
                    INSERT INTO notifications(recipient_user_id,audience,channel,status,kind,title,body,link,case_id,product_id,created_by_user_id)
                    VALUES (?,'CUSTOMER','IN_APP','UNREAD',?,?,?,?,?,?,?)
                    """,userId,kind,title,body,link,caseId,productId,actor==null?null:actor.id());
        }
        String channel=preferredChannel==null||preferredChannel.isBlank()?"MANUAL":preferredChannel.toUpperCase(Locale.ROOT);
        if(!Set.of("MANUAL","EMAIL","SMS","WHATSAPP").contains(channel)) channel="MANUAL";
        if(phone==null&&email==null) return 0;
        return jdbc.update("""
                INSERT INTO notifications(recipient_phone,recipient_email,audience,channel,status,kind,title,body,link,case_id,product_id,created_by_user_id)
                VALUES (?,?,'CUSTOMER',?,'QUEUED',?,?,?,?,?,?,?)
                """,phone,email,channel,kind,title,body,link,caseId,productId,actor==null?null:actor.id());
    }

    @Transactional
    public int notifyCustomersForProduct(UUID productId,String title,String body,String preferredChannel,AuthPrincipal actor) {
        List<UUID> cases=jdbc.query("""
                SELECT DISTINCT w.id FROM workflow_cases w
                WHERE w.product_id=? AND w.stage NOT IN ('CLOSED','CANCELLED')
                """,(rs,n)->rs.getObject(1,UUID.class),productId);
        int total=0;
        for(UUID caseId:cases) total+=notifyCustomerCase(caseId,"PRODUCT_CHANGED",title,body,"/panel/requests",preferredChannel,actor);
        if(total>0) jdbc.update("""
                INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,source,reason)
                VALUES (?,'products',?,'CUSTOMER_NOTIFICATION','APPLICATION',?)
                """,actor==null?null:actor.id(),productId.toString(),body);
        return total;
    }

    public List<NotificationRow> userNotifications(UUID userId,boolean unreadOnly) {
        String sql="""
                SELECT id,audience,channel,status,kind,title,body,link,case_id,product_id,read_at,created_at
                FROM notifications WHERE recipient_user_id=? %s ORDER BY created_at DESC LIMIT 200
                """.formatted(unreadOnly?"AND status='UNREAD'":"");
        return jdbc.query(sql,(rs,n)->new NotificationRow(
                rs.getObject("id",UUID.class),rs.getString("audience"),rs.getString("channel"),rs.getString("status"),
                rs.getString("kind"),rs.getString("title"),rs.getString("body"),rs.getString("link"),
                rs.getObject("case_id",UUID.class),rs.getObject("product_id",UUID.class),
                rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant(),rs.getTimestamp("created_at").toInstant()),userId);
    }

    public long unreadCount(UUID userId) {
        Long n=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_user_id=? AND status='UNREAD'",Long.class,userId);
        return n==null?0:n;
    }

    @Transactional
    public void markRead(UUID notificationId,UUID userId) {
        if(jdbc.update("UPDATE notifications SET status='READ',read_at=NOW() WHERE id=? AND recipient_user_id=? AND status='UNREAD'",notificationId,userId)==0) {
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=? AND recipient_user_id=?",Integer.class,notificationId,userId);
            if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"اعلان پیدا نشد.");
        }
    }

    public List<Map<String,Object>> outbox() {
        return jdbc.queryForList("""
                SELECT n.id,n.channel,n.status,n.title,n.body,n.recipient_phone,n.recipient_email,n.case_id,n.product_id,n.created_at,
                       creator.display_name AS created_by
                FROM notifications n LEFT JOIN app_users creator ON creator.id=n.created_by_user_id
                WHERE n.audience='CUSTOMER' AND n.recipient_user_id IS NULL
                ORDER BY CASE n.status WHEN 'QUEUED' THEN 0 ELSE 1 END,n.created_at DESC LIMIT 300
                """);
    }

    @Transactional
    public void markOutboxSent(UUID id,AuthPrincipal actor) {
        if(jdbc.update("UPDATE notifications SET status='SENT' WHERE id=? AND audience='CUSTOMER' AND recipient_user_id IS NULL AND status='QUEUED'",id)==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"اعلان در صف ارسال پیدا نشد.");
        jdbc.update("INSERT INTO audit_log(actor_user_id,entity_type,entity_id,action,source,reason) VALUES (?,'notifications',?,'MARK_SENT','APPLICATION','ارسال دستی تایید شد')",
                actor.id(),id.toString());
    }
}
