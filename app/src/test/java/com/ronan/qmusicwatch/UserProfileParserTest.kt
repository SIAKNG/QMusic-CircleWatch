package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.network.mergeUserProfiles
import com.ronan.qmusicwatch.network.parseUserProfile
import com.ronan.qmusicwatch.network.profileEpoch
import com.ronan.qmusicwatch.model.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileParserTest {
    @Test fun bundledAudioCannotReplaceActiveMusicMembershipOrItsDate() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":1,"identity":{"vip":1,"overdate":"2099-07-22","HugeVip":0,"LMFlag":1,"LMEnd":"2099-08-22"}}}"""))!!
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun inactiveVipCannotBecomeActiveFromAnUnrelatedOverdate() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"identity":{"vip":0,"overdate":"2099-07-22","HugeVip":0,"LMFlag":0}}"""))!!
        assertEquals(false, profile.isVip)
    }

    @Test fun officialVipQuerySeparatesSuperAndGreenExpiry() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"infoMap":{"42":{"iVipFlag":1,"iSuperVip":1,"sOverDateTime":"2099-08-22","superEndTime":"2099-08-22","HugeVip":1,"HugeVipEnd":"2099-07-22","LMFlag":1,"LMEnd":"2099-09-22"}}}"""))!!
        assertEquals("超级会员SVIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun officialVipQueryGreenIsVipAndNeverSvip() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"infoMap":{"42":{"iVipFlag":1,"iSuperVip":1,"HugeVip":0,"superEndTime":"2099-07-22"}}}"""))!!
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun greenAccountWrapperFlagCannotHideIdentityOverdate() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":1,"identity":{"vip":1,"overdate":"2099-07-22 13:07:31","LMFlag":0,"LMEnd":"","HugeVip":0,"HugeVipEnd":""},"userinfo":{"expire":-1}}}"""))!!
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
        assertEquals(true, profile.isVip)
        assertEquals(false, profile.vipLevel >= 3)
    }

    @Test fun explicitSuperProductInsideIdentityKeepsItsDate() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":1,"identity":{"vip":1,"svip":1,"overdate":"2099-07-22"}}}"""))!!
        assertEquals("超级会员SVIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun legacyTypeElevenWithOverdateIsVipWithoutAnExplicitSvipSignal() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"identity":{"vip":1,"svip":0,"vip_type":11,"level":12,"overdate":"2099-07-22"}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("green_diamond", profile.vipType)
        assertEquals(profileEpoch("2099-07-22"), profile.vipExpireAt)
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun membershipGrowthLevelCannotPromoteGreenDiamondToSvip() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"identity":{"vip":1,"svip":0,"iSuperVip":1,"music_vip_level":15,"overdate":"2099-07-22"}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
        assertEquals(false, profile.vipLevel >= 3)
    }

    @Test fun explicitSvipKeepsItsTypeAndExpiry() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"identity":{"vip":1,"svip":1,"overdate":"2099-07-22"}}"""))!!
        assertEquals("svip", profile.vipType)
        assertEquals("超级会员SVIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun inactiveSvipLabelCannotOverrideActiveVipFlags() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_info":{"vip":1,"isSvip":0,"vip_type":11,"vip_name":"超级会员","overdate":"2099-07-22"}}"""))!!
        assertEquals("green_diamond", profile.vipType)
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun snakeCaseLuxuryGreenFieldsKeepTheDate() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"is_super_green_vip":1,"super_green_vip_end_time":"2099.07.22"}"""))!!
        assertEquals("green_diamond", profile.vipType)
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }
    @Test fun parsesNicknameAndProtocolRelativeAvatar() {
        val avatar = "https://q1.qlogo.cn/g?b=qq&nk=12345&s=140"
        val profile = parseUserProfile(Json.parseToJsonElement("""{"creator":{"nick":"Ronan","logo":"$avatar"},"greenVip":{"isVip":0},"superVip":{"isSVip":1,"vipEndTime":1893456000}}"""))!!
        assertEquals("Ronan", profile.displayName)
        assertEquals(avatar, profile.avatarUrl)
        assertEquals(true, profile.isVip)
        assertEquals(1893456000L, profile.vipExpireAt)
        assertEquals("超级会员（SVIP）", profile.vipName)
    }

    @Test fun ignoresSongVipFlagsWhenParsingAccount() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"info":{"nick":"Ronan","logo":"https://q1.qlogo.cn/g?b=qq&nk=12345&s=140"},"song":{"title":"测试歌曲","isVip":0}}"""))!!
        assertEquals(null, profile.isVip)
    }

    @Test fun readsWechatAvatarFieldsAndPrefersAccountAvatarOverPlaceholder() {
        val avatar = "https://thirdwx.qlogo.cn/mmopen/abc123/132"
        val wechat = parseUserProfile(Json.parseToJsonElement("""{"profile":{"nickname":"Ronan","headimgurl":"$avatar"}}"""))!!
        assertEquals(avatar, wechat.avatarUrl)

        val merged = mergeUserProfiles(listOf(
            UserProfile(displayName = "Ronan"),
            wechat,
        ))!!
        assertEquals(avatar, merged.avatarUrl)
    }

    @Test fun parsesDedicatedVipResponse() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_info":{"vip_type":11,"vip_name":"超级会员","expire_date":"20990722"}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("超级会员（SVIP）", profile.vipName)
        assertEquals(profileEpoch("2099-07-22"), profile.vipExpireAt)
    }

    @Test fun hugeVipProductIdentifiesSuperMembership() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":1,"identity":{"vip":1,"HugeVip":1,"HugeVipEnd":"2099-07-22 23:59:59","LMFlag":1,"LMEnd":"2099-07-22 23:59:59"},"userinfo":{"expire":9,"music_level":8}}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("超级会员（SVIP）", profile.vipName)
        assertEquals(profileEpoch("2099-07-22 23:59:59"), profile.vipExpireAt)
    }

    @Test fun doesNotTreatVipExpireCounterAsUnixTimestamp() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":1,"userinfo":{"expire":9}}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals(null, profile.vipExpireAt)
    }

    @Test fun luxuryGreenDiamondIsDistinctFromInactiveSuperAndAudioProducts() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"vip_response":{"svip":0,"identity":{"HugeVip":0,"iSuperVip":1,"superEndTime":"2099-07-22 23:59:59","LMFlag":1,"LMEnd":"2099-08-22 23:59:59"}}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("豪华绿钻", profile.vipName)
        assertEquals("green_diamond", profile.vipType)
        assertEquals(profileEpoch("2099-07-22 23:59:59"), profile.vipExpireAt)
    }

    @Test fun inactiveSuperDoesNotBorrowAudioMembershipExpiry() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"identity":{"HugeVip":0,"HugeVipEnd":"2099-07-22","LMFlag":1,"LMEnd":"2099-08-22"}}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("听书会员", profile.vipName)
        assertEquals(profileEpoch("2099-08-22"), profile.vipExpireAt)
    }

    @Test fun readsOpenSdkLuxuryGreenFields() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"isSuperGreenVIP":1,"superGreenvipEndTime":4083148800}"""))!!
        assertEquals(true, profile.isVip)
        assertEquals("豪华绿钻", profile.vipName)
        assertEquals(4083148800L, profile.vipExpireAt)
    }

    @Test fun inactiveSuperVipDoesNotOverrideActiveGreenMembership() {
        val profile = parseUserProfile(Json.parseToJsonElement("""{"superVip":{"isSVip":0,"vipEndTime":2051222400},"greenVip":{"isVip":1,"vipEndTime":1893456000}}"""))!!
        assertEquals("豪华绿钻", profile.vipName)
        assertEquals(1893456000L, profile.vipExpireAt)
    }

    @Test fun mergedMembershipKeepsTypeAndExpiryFromSameHighestTier() {
        val merged = mergeUserProfiles(listOf(
            UserProfile(displayName = "Ronan", isVip = true, vipExpireAt = 1893456000, vipName = "豪华绿钻"),
            UserProfile(isVip = true, vipExpireAt = 1800000000, vipName = "超级会员（SVIP）"),
        ))!!
        assertEquals("超级会员（SVIP）", merged.vipName)
        assertEquals(1800000000L, merged.vipExpireAt)
    }

    @Test fun preservesDateTimeAndNormalizesEpochUnits() {
        val exact = profileEpoch("2026-07-22 23:59:59")!!
        val midnight = profileEpoch("2026-07-22")!!
        assertEquals(23 * 60 * 60 + 59 * 60 + 59, exact - midnight)
        assertEquals(exact, profileEpoch((exact * 1_000L).toString()))
        assertEquals(exact, profileEpoch("2026-07-22T23:59:59+08:00"))
    }
}
