package com.meridian.backend.config;

import com.meridian.backend.security.WebSocketAuthInterceptor;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PriceWebSocketHandler priceWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    private final String[] allowedOrigins;

    public WebSocketConfig(PriceWebSocketHandler priceWebSocketHandler,
                            WebSocketAuthInterceptor webSocketAuthInterceptor,
                            @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String[] allowedOrigins) {
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(priceWebSocketHandler, "/ws/prices")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins(allowedOrigins); // also blocks cross-site WebSocket hijacking
    }
}
