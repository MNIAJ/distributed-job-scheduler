package com.placements.job_scheduler.security;

import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Secures the WebSocket (STOMP) channel.
 * - CONNECT must carry a valid JWT in the "Authorization" header.
 * - The session's principal name is the user's id, which is what
 *   convertAndSendToUser() routes on.
 * - SUBSCRIBE is limited to the user's own job queue and the shared worker topic.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String WORKERS_TOPIC = "/topic/workers";
    private static final String USER_JOBS_QUEUE = "/user/queue/jobs";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Missing token");
            }
            String token = header.substring(7);
            if (!jwtUtil.isTokenValid(token)) {
                throw new MessagingException("Invalid token");
            }
            User user = userRepository.findByEmail(jwtUtil.extractEmail(token))
                    .orElseThrow(() -> new MessagingException("Unknown user"));
            accessor.setUser(new StompPrincipal(String.valueOf(user.getId())));

        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            boolean allowed = accessor.getUser() != null
                    && (WORKERS_TOPIC.equals(destination)
                    || USER_JOBS_QUEUE.equals(destination));
            if (!allowed) {
                throw new MessagingException("Subscription not allowed: " + destination);
            }
        }
        return message;
    }
}
