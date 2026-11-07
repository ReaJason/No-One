package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserImpersonationProfileTest {

    @Test
    void shouldParseEnumNamesAndBrowserShortNames() {
        assertEquals(BrowserImpersonationProfile.MAC_CHROME,
                BrowserImpersonationProfile.fromConfig("MAC_CHROME"));
        assertEquals(BrowserImpersonationProfile.MAC_CHROME,
                BrowserImpersonationProfile.fromConfig("chrome"));
        assertEquals(BrowserImpersonationProfile.MAC_CHROME,
                BrowserImpersonationProfile.fromConfig("mac-chrome"));
        assertEquals(BrowserImpersonationProfile.MAC_FIREFOX,
                BrowserImpersonationProfile.fromConfig("Firefox"));
        assertEquals(BrowserImpersonationProfile.MAC_SAFARI,
                BrowserImpersonationProfile.fromConfig("safari"));
        assertEquals(BrowserImpersonationProfile.IOS,
                BrowserImpersonationProfile.fromConfig("ios"));
        assertEquals(BrowserImpersonationProfile.ANDROID,
                BrowserImpersonationProfile.fromConfig("ANDROID"));
    }

    @Test
    void shouldTreatBlankAsNotConfigured() {
        assertNull(BrowserImpersonationProfile.fromConfig(null));
        assertNull(BrowserImpersonationProfile.fromConfig(""));
        assertNull(BrowserImpersonationProfile.fromConfig("   "));
    }

    @Test
    void shouldRejectAnUnsupportedProfile() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BrowserImpersonationProfile.fromConfig("netscape"));

        assertTrue(exception.getMessage().contains("netscape"));
        assertTrue(exception.getMessage().contains("MAC_CHROME"));
    }

    @Test
    void shouldOnlyResolveAProfileWhenImpersonationIsEnabled() {
        assertNull(BrowserImpersonationProfile.resolve(false, BrowserImpersonationProfile.ANDROID));
        assertEquals(BrowserImpersonationProfile.DEFAULT,
                BrowserImpersonationProfile.resolve(true, null));
        assertEquals(BrowserImpersonationProfile.ANDROID,
                BrowserImpersonationProfile.resolve(true, BrowserImpersonationProfile.ANDROID));
    }
}
