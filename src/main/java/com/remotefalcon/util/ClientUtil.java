package com.remotefalcon.util;

import io.vertx.ext.web.RoutingContext;

public class ClientUtil {

    public static String getClientIP(RoutingContext context) {
        String cfConnectingIp = context.request().getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.split(",")[0].trim();
        }
        return null;
    }

}
