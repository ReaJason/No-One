package com.reajason.noone.server.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpUtils {
    public final static String REGX_0_255 = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)";
    public final static String REGX_IP = "((" + REGX_0_255 + "\\.){3}" + REGX_0_255 + ")";
    public final static String REGX_IP_WILDCARD = "(((\\*\\.){3}\\*)|(" + REGX_0_255 + "(\\.\\*){3})|(" + REGX_0_255 + "\\." + REGX_0_255 + ")(\\.\\*){2}" + "|((" + REGX_0_255 + "\\.){3}\\*))";
    public final static String REGX_IP_SEG = "(" + REGX_IP + "-" + REGX_IP + ")";

    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : getMultistageReverseProxyIp(ip);
    }

    public static String getMultistageReverseProxyIp(String ip) {
        // 多级反向代理检测
        if (ip != null && ip.indexOf(",") > 0) {
            final String[] ips = ip.trim().split(",");
            for (String subIp : ips) {
                if (!isUnknown(subIp)) {
                    ip = subIp;
                    break;
                }
            }
        }
        return StringUtils.substring(ip, 0, 255);
    }

    public static boolean isUnknown(String checkString) {
        return StringUtils.isBlank(checkString) || "unknown".equalsIgnoreCase(checkString);
    }

    public static boolean isIP(String ip) {
        return StringUtils.isNotBlank(ip) && ip.matches(REGX_IP);
    }

    public static String normalizeExactIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return null;
        }
        String candidate = getMultistageReverseProxyIp(ip.trim());
        if (isIP(candidate)) {
            return candidate;
        }
        if (candidate.contains("*") || candidate.contains("-") || candidate.contains("/")) {
            return null;
        }
        if (!candidate.contains(":")) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (address instanceof Inet6Address) {
                return address.getHostAddress();
            }
            return null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static boolean isIpWildCard(String ip) {
        return StringUtils.isNotBlank(ip) && ip.matches(REGX_IP_WILDCARD);
    }

    public static boolean ipIsInWildCardNoCheck(String ipWildCard, String ip) {
        String[] s1 = ipWildCard.split("\\.");
        String[] s2 = ip.split("\\.");
        boolean isMatchedSeg = true;
        for (int i = 0; i < s1.length && !s1[i].equals("*"); i++) {
            if (!s1[i].equals(s2[i])) {
                isMatchedSeg = false;
                break;
            }
        }
        return isMatchedSeg;
    }

    public static boolean isIPSegment(String ipSeg) {
        return StringUtils.isNotBlank(ipSeg) && ipSeg.matches(REGX_IP_SEG);
    }

    public static boolean ipIsInNetNoCheck(String iparea, String ip) {
        int idx = iparea.indexOf('-');
        String[] sips = iparea.substring(0, idx).split("\\.");
        String[] sipe = iparea.substring(idx + 1).split("\\.");
        String[] sipt = ip.split("\\.");
        long ips = 0L, ipe = 0L, ipt = 0L;
        for (int i = 0; i < 4; ++i) {
            ips = ips << 8 | Integer.parseInt(sips[i]);
            ipe = ipe << 8 | Integer.parseInt(sipe[i]);
            ipt = ipt << 8 | Integer.parseInt(sipt[i]);
        }
        if (ips > ipe) {
            long t = ips;
            ips = ipe;
            ipe = t;
        }
        return ips <= ipt && ipt <= ipe;
    }

    public static boolean isMatchedIp(String filter, String ip) {
        if (StringUtils.isEmpty(filter) || StringUtils.isEmpty(ip)) {
            return false;
        }
        String[] ips = filter.split(";");
        for (String iStr : ips) {
            if (isIP(iStr) && iStr.equals(ip)) {
                return true;
            } else if (isIpWildCard(iStr) && ipIsInWildCardNoCheck(iStr, ip)) {
                return true;
            } else if (isIPSegment(iStr) && ipIsInNetNoCheck(iStr, ip)) {
                return true;
            }
        }
        return false;
    }
}
