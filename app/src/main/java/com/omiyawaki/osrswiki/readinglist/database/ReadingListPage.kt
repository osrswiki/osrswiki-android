package com.omiyawaki.osrswiki.readinglist.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.StringUtils
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.util.StringUtil
import java.io.Serializable

@Entity
data class ReadingListPage(
    val wiki: WikiSite, // This will be stored as String by TypeConverter
    val namespace: Namespace, // This will be stored as String by TypeConverter
    var displayTitle: String,
    var apiTitle: String, // This is the prefixedText for PageTitle
    var description: String? = null,
    var thumbUrl: String? = null,
    var listId: Long = -1,
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var mtime: Long = 0,
    var atime: Long = 0,
    var offline: Boolean = Prefs.isDownloadingReadingListArticlesEnabled,
    var status: Long = STATUS_QUEUE_FOR_SAVE,
    var sizeBytes: Long = 0,
    var lang: String,
    var revId: Long = 0,
    var remoteId: Long = 0
) : Serializable {

    constructor(title: PageTitle) :
            this(title.wikiSite, title.namespace(), title.displayText, title.prefixedText, // Use title.prefixedText for apiTitle
                title.description, title.thumbUrl, lang = title.wikiSite.languageCode) {
        val now = System.currentTimeMillis()
        mtime = now
        atime = now
    }

    @delegate:Transient
    val accentInvariantTitle: String by lazy {
        StringUtils.stripAccents(StringUtil.fromHtml(displayTitle).toString())
    }

    @Transient @Volatile var downloadProgress: Int = 0
    @Transient @Volatile var selected: Boolean = false

    val saving get() = offline && (status == STATUS_QUEUE_FOR_SAVE || status == STATUS_QUEUE_FOR_FORCED_SAVE)

    fun touch() {
        atime = System.currentTimeMillis()
    }

    companion object {
        const val STATUS_QUEUE_FOR_SAVE = 0L
        const val STATUS_SAVED = 1L
        const val STATUS_QUEUE_FOR_DELETE = 2L
        const val STATUS_QUEUE_FOR_FORCED_SAVE = 3L

        fun toPageTitle(page: ReadingListPage): PageTitle {
            val wiki = page.wiki.apply { if (this.languageCode != page.lang) this.languageCode = page.lang }
            // Corrected PageTitle constructor call using named arguments
            return PageTitle(
                namespace = page.namespace,
                text = page.apiTitle, // apiTitle from ReadingListPage is the prefixed text for PageTitle
                wikiSite = wiki,
                thumbUrl = page.thumbUrl,
                description = page.description,
                displayText = page.displayTitle
                // fragment can be null by default if not stored in ReadingListPage
            )
        }
    }
}
