package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.model.*
import com.ronan.qmusicwatch.network.mergeProfileIdentity
import org.junit.Assert.*
import org.junit.Test

class ProfileIdentityTest {
    @Test fun anEarlyIdentityDoesNotGrantUnverifiedMembership() {
        val partial = UserProfile(displayName = "名字", avatarUrl = "avatar", isVip = true, vipLevel = 3)
        val result = mergeProfileIdentity(null, partial)
        assertEquals("名字", result.displayName); assertEquals("avatar", result.avatarUrl)
        assertNull(result.isVip); assertEquals(0, result.vipLevel); assertTrue(result.qualityEntitlements.isEmpty())
    }
    @Test fun identityRefreshPreservesVerifiedRightsAndExistingNonemptyFields() {
        val current = UserProfile(displayName = "旧名字", avatarUrl = "avatar", isVip = true, vipLevel = 3, vipExpireAt = 2000000000)
        val result = mergeProfileIdentity(current, UserProfile(displayName = "新名字", isVip = false))
        assertEquals("新名字", result.displayName); assertEquals("avatar", result.avatarUrl)
        assertEquals(true, result.isVip); assertEquals(3, result.vipLevel); assertEquals(current.vipExpireAt, result.vipExpireAt)
    }
}
