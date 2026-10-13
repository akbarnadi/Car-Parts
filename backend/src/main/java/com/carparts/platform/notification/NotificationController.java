package com.carparts.platform.notification;

import com.carparts.platform.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications){this.notifications=notifications;}

    @GetMapping
    public List<NotificationService.NotificationRow> list(@RequestParam(defaultValue="false") boolean unreadOnly, Authentication a){
        return notifications.userNotifications(p(a).id(),unreadOnly);
    }

    @PutMapping("/{id}/read")
    public void read(@PathVariable UUID id,Authentication a){notifications.markRead(id,p(a).id());}

    private AuthPrincipal p(Authentication a){return (AuthPrincipal)a.getPrincipal();}
}
