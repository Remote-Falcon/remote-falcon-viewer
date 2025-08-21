package com.remotefalcon.util;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientUtilTest {

    @Test
    @DisplayName("Returns CF-Connecting-IP first token when present")
    void returnsCfConnectingIp() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.1, 198.51.100.2");

        String ip = ClientUtil.getClientIP(ctx);
        assertEquals("203.0.113.1", ip);
    }

    @Test
    @DisplayName("Falls back to X-Forwarded-For skipping 'unknown'")
    void returnsFromXForwardedForSkippingUnknown() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(req.getHeader("X-Forwarded-For")).thenReturn("unknown, 8.8.8.8");

        String ip = ClientUtil.getClientIP(ctx);
        assertEquals("8.8.8.8", ip);
    }

    @Test
    @DisplayName("Uses X-Real-IP and sanitizes quotes and port")
    void usesXRealIpSanitized() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn("\"127.0.0.1:12345\"");

        String ip = ClientUtil.getClientIP(ctx);
        assertEquals("127.0.0.1", ip);
    }

    @Test
    @DisplayName("Parses Forwarded header and strips IPv6 brackets")
    void parsesForwardedHeader() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn(null);
        when(req.getHeader("Forwarded")).thenReturn("for=192.0.2.43, for=\"[2001:db8:cafe::17]\"");

        String ip = ClientUtil.getClientIP(ctx);
        assertEquals("192.0.2.43", ip);
    }

    @Test
    @DisplayName("Falls back to remoteAddress.host when headers absent")
    void fallsBackToRemoteAddress() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        SocketAddress address = mock(SocketAddress.class);
        when(ctx.request()).thenReturn(req);
        when(req.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn(null);
        when(req.getHeader("Forwarded")).thenReturn(null);
        when(req.remoteAddress()).thenReturn(address);
        when(address.host()).thenReturn("5.6.7.8");

        String ip = ClientUtil.getClientIP(ctx);
        assertEquals("5.6.7.8", ip);
    }

    @Test
    @DisplayName("Returns null when context or request is null")
    void returnsNullOnNullContextOrRequest() {
        assertNull(ClientUtil.getClientIP(null));

        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(null);
        assertNull(ClientUtil.getClientIP(ctx));
    }
}
