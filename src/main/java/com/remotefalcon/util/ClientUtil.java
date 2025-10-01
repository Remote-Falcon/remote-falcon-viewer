package com.remotefalcon.util;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class ClientUtil {

  public static String getClientIP(RoutingContext context) {
    if (context == null) {
      return null;
    }

    HttpServerRequest request = context.request();
    if (request == null) {
      return null;
    }

    // 1) Cloudflare header
    String ip = firstIpFromHeader(request.getHeader("CF-Connecting-IP"));
    if (ip != null)
      return ip;

    // 2) Standard reverse-proxy header
    ip = firstIpFromHeader(request.getHeader("X-Forwarded-For"));
    if (ip != null)
      return ip;

    // 3) Nginx/Heroku style
    ip = firstIpFromHeader(request.getHeader("X-Real-IP"));
    if (ip != null)
      return ip;

    // 4) RFC 7239 Forwarded: for=...
    String forwarded = request.getHeader("Forwarded");
    if (forwarded != null && !forwarded.isEmpty()) {
      // Example: Forwarded: for=192.0.2.43, for="[2001:db8:cafe::17]"
      for (String part : forwarded.split(",")) {
        String[] kvs = part.trim().split(";");
        for (String kv : kvs) {
          String[] pair = kv.trim().split("=", 2);
          if (pair.length == 2 && pair[0].equalsIgnoreCase("for")) {
            String candidate = sanitizeIp(pair[1]);
            if (!candidate.isEmpty())
              return candidate;
          }
        }
      }
    }

    // 5) Fallback to remote address (useful when testing locally without proxies)
    if (request.remoteAddress() != null && request.remoteAddress().host() != null) {
      return request.remoteAddress().host();
    }

    return null;
  }

  private static String firstIpFromHeader(String headerValue) {
    if (headerValue == null || headerValue.isEmpty())
      return null;
    // X-Forwarded-For may contain multiple addresses, take the first non-empty
    // token
    for (String token : headerValue.split(",")) {
      String candidate = sanitizeIp(token);
      if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static String sanitizeIp(String raw) {
    String s = raw == null ? "" : raw.trim();
    // Strip surrounding quotes
    if (s.startsWith("\"") && s.endsWith("\"")) {
      s = s.substring(1, s.length() - 1);
    }
    // Strip IPv6 brackets if present: [::1]
    if (s.startsWith("[") && s.endsWith("]")) {
      s = s.substring(1, s.length() - 1);
    }
    // Remove optional port suffix if present (e.g., 127.0.0.1:12345)
    int colonIdx = s.indexOf(":");
    if (colonIdx > -1 && s.chars().filter(ch -> ch == ':').count() == 1) {
      // Only strip for IPv4 host:port; IPv6 uses multiple colons and often brackets
      s = s.substring(0, colonIdx);
    }
    return s;
  }
}
