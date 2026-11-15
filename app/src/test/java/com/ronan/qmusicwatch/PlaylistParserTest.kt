package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.model.LibraryData
import com.ronan.qmusicwatch.model.MusicCollection
import com.ronan.qmusicwatch.model.Track
import com.ronan.qmusicwatch.network.mergeLibraryPlaylists
import com.ronan.qmusicwatch.network.normalizeLibraryData
import com.ronan.qmusicwatch.network.parseAccountPlaylists
import com.ronan.qmusicwatch.network.playlistDirectoryNumber
import com.ronan.qmusicwatch.network.parseFavoritePlaylists
import com.ronan.qmusicwatch.network.qqPlaylistDetailIdentity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistParserTest {
    @Test fun validatesWritablePlaylistDirectoryIds() {
        assertEquals(201L, playlistDirectoryNumber("201"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { playlistDirectoryNumber("") }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { playlistDirectoryNumber("not-a-number") }
    }
    @Test fun parsesAccountPlaylistIdsWithoutMixingTidAndDirId() {
        val artwork = "https://y.gtimg.cn/music/photo_new/T002R300x300M000albumMID123.jpg"
        val root = Json.parseToJsonElement("""{"data":{"v_playlist":[{"dirId":202,"dirName":"通勤","tid":987654,"songNum":12,"picUrl":"$artwork"}]}}""")
        val item = parseAccountPlaylists(root).single()
        assertEquals("987654", item.id)
        assertEquals("202", item.directoryId)
        assertEquals("通勤", item.title)
        assertEquals(12, item.trackCount)
        assertEquals(artwork, item.artworkUrl)
        assertEquals(true, item.owned)
    }

    @Test fun separatesCreatedAndCollectedPlaylists() {
        val root = Json.parseToJsonElement("""{"data":{"v_playlist":[{"dirId":1,"dirName":"我的","tid":11}],"v_collect":[{"dirId":2,"dirName":"收藏","tid":22}]}}""")
        val items = parseAccountPlaylists(root).associateBy { it.title }
        assertEquals(true, items["我的"]?.owned)
        assertEquals(false, items["收藏"]?.owned)
    }

    @Test fun parsesDedicatedFavoritePlaylistResponseAsReadOnly() {
        val artwork = "https://y.gtimg.cn/music/photo_new/T002R300x300M000favoriteMID456.jpg"
        val root = Json.parseToJsonElement("""{"v_list":[{"tid":"9988","dissname":"收藏的歌单","songnum":42,"picurl":"$artwork"}]}""")
        val item = parseFavoritePlaylists(root).single()
        assertEquals("9988", item.id)
        assertEquals("收藏的歌单", item.title)
        assertEquals(42, item.trackCount)
        assertEquals(false, item.owned)
        assertEquals(artwork, item.artworkUrl)
    }

    @Test fun removesSystemLikedPlaylistButKeepsOwnedPlaylistWithSameTitle() {
        val root = Json.parseToJsonElement(
            """{"data":{"v_playlist":[{"dirId":201,"dirName":"我喜欢","tid":0},{"dirId":202,"dirName":"我喜欢","tid":9988},{"dirId":203,"dirName":"编号碰撞","tid":201}]}}""",
        )

        val items = parseAccountPlaylists(root)

        assertEquals(listOf("202", "203"), items.map(MusicCollection::directoryId))
        assertEquals(listOf("9988", "201"), items.map(MusicCollection::id))
        assertEquals(listOf("我喜欢", "编号碰撞"), items.map(MusicCollection::title))
        assertEquals(listOf(true, true), items.map(MusicCollection::owned))
    }

    @Test fun removesSystemLikedPlaylistFromDedicatedFavoritesWithoutHidingRealSameNameCollection() {
        val root = Json.parseToJsonElement(
            """{"v_list":[{"tid":"201","dissname":"默认收藏","songnum":3},{"tid":"0","dissname":"我喜欢的音乐","songnum":4},{"tid":"7788","dissname":" 我 喜欢 ","songnum":5},{"tid":"9988","dissname":"我喜欢的旅行","songnum":8}]}""",
        )

        val items = parseFavoritePlaylists(root)

        assertEquals(listOf("7788", "9988"), items.map { it.id })
        assertEquals(listOf(" 我 喜欢 ", "我喜欢的旅行"), items.map { it.title })
    }

    @Test fun removesSystemLikedPlaylistFromCachedLibraryData() {
        val liked = listOf(Track("song-1", "歌曲"))
        val cached = LibraryData(
            liked = liked,
            playlists = listOf(
                MusicCollection("liked", "我喜欢的音乐", directoryId = "201", owned = false),
                MusicCollection("travel", "旅行", directoryId = "302", owned = false),
                MusicCollection("travel-alias", "旅行重复项", directoryId = "302", owned = false),
            ),
        )

        val normalized = normalizeLibraryData(cached)

        assertEquals(liked, normalized.liked)
        assertEquals(listOf("travel"), normalized.playlists.map(MusicCollection::id))
    }

    @Test fun removesExpiredGatewayArtworkFromCachedLikedTracks() {
        val oldArtwork = "https://heyboxlite.xyz/api/qmusic-watch/gateway/media/${"a".repeat(32)}/cover.jpg"
        val cached = LibraryData(
            liked = listOf(Track("00485V8K4InqbZ", "水星记", artworkUrl = oldArtwork)),
            playlists = emptyList(),
        )

        val normalized = normalizeLibraryData(cached)

        assertEquals("", normalized.liked.single().artworkUrl)
    }

    @Test fun usesDirectoryOnlyForOwnedPlaylistAndDissIdOnlyForCollectedPlaylist() {
        val owned = qqPlaylistDetailIdentity(
            MusicCollection("987654", "我的歌单", directoryId = "202", owned = true),
        )
        val collected = qqPlaylistDetailIdentity(
            MusicCollection("99887766", "收藏歌单", directoryId = "99887766", owned = false),
        )

        assertEquals(0L, owned.dissId)
        assertEquals(202L, owned.directoryId)
        assertEquals(99887766L, collected.dissId)
        assertEquals(0L, collected.directoryId)
    }

    @Test fun favoritesWithSharedDirectoryIdsKeepTheirOwnPlaylistIds() {
        val account = listOf(
            MusicCollection("created", "自建", directoryId = "202", owned = true),
            MusicCollection("9988", "旧收藏信息", directoryId = "302", owned = false),
            MusicCollection("8877", "同目录的另一收藏", directoryId = "302", owned = false),
        )
        val favorites = listOf(
            MusicCollection("9988", "收藏歌单", directoryId = "402", owned = false),
            MusicCollection("7766", "另一收藏", directoryId = "202", owned = false),
        )

        val merged = mergeLibraryPlaylists(account, favorites)

        assertEquals(listOf("created", "8877", "9988", "7766"), merged.map(MusicCollection::id))
        assertEquals(listOf("自建", "同目录的另一收藏", "收藏歌单", "另一收藏"), merged.map(MusicCollection::title))
    }
}
