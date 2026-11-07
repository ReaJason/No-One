package com.reajason.noone.core.client;

import com.github.zhkl0228.impersonator.ImpersonatorApi;
import com.github.zhkl0228.impersonator.ImpersonatorFactory;

import java.util.Locale;

/**
 * Browser profile used by the TLS/JA3/JA4 and HTTP/2 fingerprint impersonation.
 *
 * <p>The name stored in {@code clientConfig.impersonationProfile} is matched case-insensitively and
 * accepts both the enum name ({@code MAC_CHROME}) and the browser short name ({@code chrome}).</p>
 *
 * @author ReaJason
 */
public enum BrowserImpersonationProfile {

    MAC_CHROME,
    MAC_FIREFOX,
    MAC_SAFARI,
    IOS,
    ANDROID;

    /**
     * The profile that is used when impersonation is switched on but no profile was selected.
     */
    public static final BrowserImpersonationProfile DEFAULT = MAC_CHROME;

    /**
     * Parses a configured profile name.
     *
     * @param raw the configured value, may be {@code null} or blank
     * @return the matching profile, or {@code null} when {@code raw} is blank
     * @throws IllegalArgumentException when the value names no supported profile
     */
    public static BrowserImpersonationProfile fromConfig(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        switch (normalized) {
            case "CHROME":
            case "MACCHROME":
                return MAC_CHROME;
            case "FIREFOX":
            case "MACFIREFOX":
                return MAC_FIREFOX;
            case "SAFARI":
            case "MACSAFARI":
                return MAC_SAFARI;
            case "IOS":
                return IOS;
            case "ANDROID":
                return ANDROID;
            default:
                for (BrowserImpersonationProfile profile : values()) {
                    if (profile.name().equals(normalized)) {
                        return profile;
                    }
                }
                throw new IllegalArgumentException(
                        "Unsupported browser impersonation profile '" + raw + "'. Supported values: "
                                + supportedValues());
        }
    }

    /**
     * Resolves the profile a client should use.
     *
     * @param enabled  whether {@code browserImpersonation} is switched on
     * @param selected the configured profile, may be {@code null}
     * @return {@code null} when impersonation is off, otherwise the selected profile or the default
     */
    public static BrowserImpersonationProfile resolve(boolean enabled, BrowserImpersonationProfile selected) {
        if (!enabled) {
            return null;
        }
        return selected != null ? selected : DEFAULT;
    }

    /**
     * Creates the upstream impersonator API for this profile. The caller owns the returned instance;
     * it is safe to reuse it for several clients, but a profile object is cheap and carries per
     * connection state, so clients of different shells must not share one.
     */
    public ImpersonatorApi newApi() {
        switch (this) {
            case MAC_CHROME:
                return ImpersonatorFactory.macChrome();
            case MAC_FIREFOX:
                return ImpersonatorFactory.macFirefox();
            case MAC_SAFARI:
                return ImpersonatorFactory.macSafari();
            case IOS:
                return ImpersonatorFactory.ios();
            case ANDROID:
                return ImpersonatorFactory.android();
            default:
                throw new IllegalStateException("Unhandled browser impersonation profile: " + this);
        }
    }

    private static String supportedValues() {
        StringBuilder builder = new StringBuilder();
        for (BrowserImpersonationProfile profile : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(profile.name());
        }
        return builder.toString();
    }
}
