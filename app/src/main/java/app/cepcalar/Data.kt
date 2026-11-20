package app.cepcalar

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Song(
    val mediaId: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val artUrl: String? = null,
    val remote: Boolean = false,
) {
    val statKey: String get() = "$title|$artist".lowercase()
}

fun Song.toItem(): MediaItem {
    val extras = Bundle().apply {
        putBoolean("video", isVideo)
        putBoolean("remote", remote)
        putString("art", artUrl)
        putString("album", album)
        putLong("dur", durationMs)
    }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.ifEmpty { null })
                .setExtras(extras)
                .build()
        )
        .build()
}

fun songFromItem(i: MediaItem): Song {
    val md = i.mediaMetadata
    val ex = md.extras
    return Song(
        mediaId = i.mediaId,
        uri = i.localConfiguration?.uri ?: i.requestMetadata.mediaUri ?: Uri.EMPTY,
        title = md.title?.toString() ?: "",
        artist = md.artist?.toString() ?: "",
        album = ex?.getString("album") ?: "",
        durationMs = ex?.getLong("dur") ?: 0L,
        isVideo = ex?.getBoolean("video") == true,
        artUrl = ex?.getString("art"),
        remote = ex?.getBoolean("remote") == true,
    )
}

data class LrcLine(val timeMs: Long, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LrcLine>)
data class Artist(val id: Long, val name: String, val pic: String)

/** Telefondaki müzik ve klipleri MediaStore üzerinden otomatik bulur. */
object Library {
    private fun clean(s: String?): String {
        val v = s?.trim() ?: ""
        return if (v.isEmpty() || v == "<unknown>" || v.equals("unknown", true)) "" else v
    }

    private fun split(title: String, artist: String): Pair<String, String> {
        if (artist.isEmpty()) {
            val i = title.indexOf(" - ")
            if (i > 0) return Pair(title.substring(i + 3).trim(), title.substring(0, i).trim())
        }
        return Pair(title, artist)
    }

    fun scan(ctx: Context, allVideos: Boolean): List<Song> {
        val out = ArrayList<Song>()
        val cr = ctx.contentResolver

        runCatching {
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
            )
            cr.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000",
                null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val (t, a) = split(clean(c.getString(1)), clean(c.getString(2)))
                    out.add(
                        Song(
                            mediaId = "a$id",
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            title = t.ifEmpty { "Adsız" }, artist = a, album = clean(c.getString(3)),
                            durationMs = c.getLong(4), isVideo = false,
                        )
                    )
                }
            }
        }

        runCatching {
            val proj = arrayOf(
                MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.ARTIST, MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.RELATIVE_PATH,
            )
            val musicPath = Regex("(?i)(music|müzik|muzik|klip|clip|şarkı|sarki|song)")
            val skipPath = Regex("(?i)(dcim|whatsapp|camera|screen|telegram|snapchat|instagram)")
            cr.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Video.Media.DURATION} >= 30000", null,
                "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = (c.getString(1) ?: "").substringBeforeLast('.')
                    val artistRaw = clean(c.getString(2))
                    val path = c.getString(4) ?: ""
                    val ok = allVideos || artistRaw.isNotEmpty() ||
                        (musicPath.containsMatchIn(path) && !skipPath.containsMatchIn(path))
                    if (!ok) continue
                    val (t, a) = split(name.replace('_', ' '), artistRaw)
                    out.add(
                        Song(
                            mediaId = "v$id",
                            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            title = t.ifEmpty { "Adsız klip" }, artist = a, album = "",
                            durationMs = c.getLong(3), isVideo = true,
                        )
                    )
                }
            }
        }
        return out
    }
}

/** Dinleme sayıları ve ayarlar. */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("cep", Context.MODE_PRIVATE)

    fun loadStats(c: Context): Map<String, Int> {
        val j = runCatching { JSONObject(sp(c).getString("stats", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val m = HashMap<String, Int>()
        for (k in j.keys()) m[k] = j.optInt(k, 0)
        return m
    }

    fun inc(c: Context, key: String): Map<String, Int> {
        val m = loadStats(c).toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        sp(c).edit().putString("stats", JSONObject(m as Map<*, *>).toString()).apply()
        return m
    }

    fun getAllVideos(c: Context) = sp(c).getBoolean("allVideos", false)
    fun setAllVideos(c: Context, v: Boolean) = sp(c).edit().putBoolean("allVideos", v).apply()
}

/** İnternet: sözler (lrclib.net) ve benzer sanatçılar (Deezer). İkisi de anahtarsız. */
object Net {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "CepCalar/1.0")
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    private fun parseLrc(raw: String): Lyrics? {
        if (raw.length < 2) return null
        val body = raw.substring(1)
        if (raw[0] == 'S') {
            val re = Regex("\\[(\\d+):(\\d+(?:[.,]\\d+)?)\\]")
            val out = ArrayList<LrcLine>()
            for (line in body.lines()) {
                val text = line.replace(Regex("\\[[^\\]]*\\]"), "").trim()
                for (m in re.findAll(line)) {
                    val t = (m.groupValues[1].toLong() * 60000 +
                        (m.groupValues[2].replace(',', '.').toDouble() * 1000).toLong())
                    out.add(LrcLine(t, text))
                }
            }
            out.sortBy { it.timeMs }
            return if (out.isEmpty()) null else Lyrics(true, out)
        }
        return Lyrics(false, body.lines().map { LrcLine(-1, it) })
    }

    fun lyrics(ctx: Context, s: Song): Lyrics? {
        val f = File(ctx.filesDir, "lrc_" + s.statKey.hashCode() + ".txt")
        if (f.exists()) return parseLrc(f.readText())
        var o: JSONObject? = null
        if (s.artist.isNotEmpty()) {
            val body = get(
                "https://lrclib.net/api/get?artist_name=${enc(s.artist)}&track_name=${enc(s.title)}" +
                    "&duration=${s.durationMs / 1000}"
            )
            o = body?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
        if (o == null) {
            val body = get("https://lrclib.net/api/search?q=" + enc((s.artist + " " + s.title).trim()))
            val arr = body?.let { runCatching { JSONArray(it) }.getOrNull() }
            if (arr != null && arr.length() > 0) o = arr.optJSONObject(0)
        }
        if (o == null) return null
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val raw = when {
            synced != null -> "S$synced"
            plain != null -> "P$plain"
            else -> return null
        }
        runCatching { f.writeText(raw) }
        return parseLrc(raw)
    }

    fun searchArtist(name: String): Artist? {
        val body = get("https://api.deezer.com/search/artist?q=${enc(name)}&limit=1") ?: return null
        val d = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return null
        val a = d.optJSONObject(0) ?: return null
        return Artist(a.optLong("id"), a.optString("name"), a.optString("picture_medium"))
    }

    fun related(id: Long, limit: Int = 8): List<Artist> {
        val body = get("https://api.deezer.com/artist/$id/related?limit=$limit") ?: return emptyList()
        val d = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Artist>()
        for (i in 0 until d.length()) {
            val a = d.optJSONObject(i) ?: continue
            out.add(Artist(a.optLong("id"), a.optString("name"), a.optString("picture_medium")))
        }
        return out
    }

    fun topTracks(id: Long): List<Song> {
        val body = get("https://api.deezer.com/artist/$id/top?limit=15") ?: return emptyList()
        val d = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Song>()
        for (i in 0 until d.length()) {
            val t = d.optJSONObject(i) ?: continue
            val prev = t.optString("preview")
            if (prev.isBlank()) continue
            out.add(
                Song(
                    mediaId = "d" + t.optLong("id"), uri = Uri.parse(prev),
                    title = t.optString("title"),
                    artist = t.optJSONObject("artist")?.optString("name") ?: "",
                    album = t.optJSONObject("album")?.optString("title") ?: "",
                    durationMs = 30000, isVideo = false,
                    artUrl = t.optJSONObject("album")?.optString("cover_medium"), remote = true,
                )
            )
        }
        return out
    }
}
