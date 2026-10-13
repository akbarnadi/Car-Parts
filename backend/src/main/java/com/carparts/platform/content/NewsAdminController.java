package com.carparts.platform.content;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/admin/news")
public class NewsAdminController {
    private final JdbcTemplate jdbc; public NewsAdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record NewsInput(String slug,String titleFa,String summaryFa,String bodyFa,String status){}
    @GetMapping public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT * FROM news_posts ORDER BY created_at DESC");}
    @PostMapping public Map<String,Object> create(@RequestBody NewsInput in,Authentication a){validate(in);UUID id=jdbc.queryForObject("INSERT INTO news_posts(slug,title_fa,summary_fa,body_fa,status,published_at,created_by_user_id) VALUES (?,?,?,?,?,CASE WHEN ?='PUBLISHED' THEN NOW() ELSE NULL END,?) RETURNING id",UUID.class,in.slug().trim(),in.titleFa().trim(),in.summaryFa(),in.bodyFa().trim(),in.status(),in.status(),((AuthPrincipal)a.getPrincipal()).id());return jdbc.queryForMap("SELECT * FROM news_posts WHERE id=?",id);}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID id,@RequestBody NewsInput in){validate(in);if(jdbc.update("UPDATE news_posts SET slug=?,title_fa=?,summary_fa=?,body_fa=?,status=?,published_at=CASE WHEN ?='PUBLISHED' THEN COALESCE(published_at,NOW()) ELSE published_at END,updated_at=NOW() WHERE id=?",in.slug().trim(),in.titleFa().trim(),in.summaryFa(),in.bodyFa().trim(),in.status(),in.status(),id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return jdbc.queryForMap("SELECT * FROM news_posts WHERE id=?",id);}
    private void validate(NewsInput in){if(in==null||in.slug()==null||in.slug().isBlank()||in.titleFa()==null||in.titleFa().isBlank()||in.bodyFa()==null||in.bodyFa().isBlank()||!Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(in.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"اطلاعات خبر کامل نیست.");}
}
