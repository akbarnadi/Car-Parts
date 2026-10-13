package com.carparts.platform.content;

import com.carparts.platform.auth.AuthPrincipal;
import com.carparts.platform.notification.NotificationService;
import com.carparts.platform.workflow.WorkflowAutomationService;
import com.carparts.platform.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/public")
public class PublicContentController {
    private final JdbcTemplate jdbc;
    private final WorkflowService workflow;
    private final WorkflowAutomationService automation;
    private final NotificationService notifications;

    public PublicContentController(JdbcTemplate jdbc,WorkflowService workflow,WorkflowAutomationService automation,NotificationService notifications){
        this.jdbc=jdbc;this.workflow=workflow;this.automation=automation;this.notifications=notifications;
    }

    public record ContactInput(String fullName,String phone,String email,String subject,String message){}

    @GetMapping("/news")
    public List<Map<String,Object>> news(){
        return jdbc.queryForList("SELECT id,slug,title_fa,summary_fa,body_fa,published_at FROM news_posts WHERE status='PUBLISHED' ORDER BY published_at DESC NULLS LAST,created_at DESC LIMIT 50");
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> contact(@RequestBody ContactInput in){
        if(in.fullName()==null||in.fullName().isBlank()||in.message()==null||in.message().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"نام و متن پیام الزامی است.");
        UUID id=jdbc.queryForObject("INSERT INTO contact_messages(full_name,phone,email,subject,message) VALUES (?,?,?,?,?) RETURNING id",UUID.class,
                in.fullName().trim(),blank(in.phone()),blank(in.email()),blank(in.subject()),in.message().trim());
        notifications.notifyRoles(Set.of("SUPER_ADMIN","MANAGER","ADMIN","SUPPORT"),"NEW_CONTACT_MESSAGE","پیام جدید مشتری",
                "یک پیام جدید از بخش تماس با ما ثبت شد.","/admin/inbox",null,null,null);
        return Map.of("id",id,"message","پیام شما ثبت شد.");
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowService.CaseRow request(@RequestBody WorkflowService.CreateRequest in,Authentication authentication){
        if(in.contactPhone()==null||in.contactPhone().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"شماره تماس برای ثبت درخواست الزامی است.");
        WorkflowService.CaseRow row=workflow.createSite(in);
        AuthPrincipal actor=null;
        if(authentication!=null&&authentication.getPrincipal() instanceof AuthPrincipal p){
            actor=p;
            jdbc.update("UPDATE customer_requests SET customer_id=(SELECT id FROM customers WHERE user_id=?) WHERE request_no=?",p.id(),row.requestNo());
        }
        automation.afterRequest(row,actor);
        return row;
    }

    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
}
