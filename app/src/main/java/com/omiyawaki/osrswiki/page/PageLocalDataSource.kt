package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PageLocalDataSource(
    private val articleMetaDao: ArticleMetaDao,
    private val applicationContext: Context
) {
    companion object {
        private const val ARTICLES_DIR_NAME = "osrs_wiki_articles"
        private const val HTML_EXTENSION = ".html"
        private const val TAG = "PageLocalDataSource"
    }

    suspend fun getArticleFromCache(pageId: Int): PageUiState? {
        return withContext(Dispatchers.IO) {
            try {
                val localMeta = articleMetaDao.getMetaByPageId(pageId)
                if (localMeta != null && localMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(localMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.i(TAG, "Found pageId: $pageId ('${localMeta.title}') in local cache.")
                        val htmlContent = localFile.readText()
                        return@withContext PageUiState(
                            isLoading = false, error = null, imageUrl = null,
                            pageId = localMeta.pageId,
                            title = localMeta.title,
                            plainTextTitle = localMeta.title,
                            htmlContent = htmlContent, // Saved content is already full document
                            wikiUrl = localMeta.wikiUrl,
                            revisionId = localMeta.revisionId,
                            lastFetchedTimestamp = localMeta.lastFetchedTimestamp,
                            localFilePath = localMeta.localFilePath,
                            isCurrentlyOffline = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading pageId: $pageId from local cache.", e)
            }
            null
        }
    }
    
    suspend fun getArticleFromCache(title: String): PageUiState? {
        return withContext(Dispatchers.IO) {
            try {
                val localMeta = articleMetaDao.getMetaByExactTitle(title)
                if (localMeta != null && localMeta.pageId != null) {
                    // Delegate to the pageId version to avoid code duplication
                    return@withContext getArticleFromCache(localMeta.pageId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading title: '$title' from local cache.", e)
            }
            null
        }
    }

    suspend fun saveArticle(
        pageId: Int,
        canonicalTitle: String,
        revisionId: Long?,
        fullHtmlContent: String
    ) {
        withContext(Dispatchers.IO) {
            val articleUrl = "https://oldschool.runescape.wiki/w/${canonicalTitle.replace(" ", "_")}"
            val fileName = "$pageId$HTML_EXTENSION"
            val articlesDir = File(applicationContext.filesDir, ARTICLES_DIR_NAME)
            if (!articlesDir.exists()) {
                articlesDir.mkdirs()
            }
            val articleFile = File(articlesDir, fileName)
            articleFile.writeText(fullHtmlContent)

            val currentTime = System.currentTimeMillis()
            val existingMeta = articleMetaDao.getMetaByPageId(pageId)

            if (existingMeta != null) {
                val updatedMeta = existingMeta.copy(
                    title = canonicalTitle, wikiUrl = articleUrl,
                    localFilePath = articleFile.absolutePath, lastFetchedTimestamp = currentTime,
                    revisionId = revisionId
                )
                articleMetaDao.update(updatedMeta)
                Log.i(TAG, "Successfully updated metadata for '$canonicalTitle'.")
            } else {
                val newMeta = ArticleMetaEntity(
                    pageId = pageId, title = canonicalTitle,
                    wikiUrl = articleUrl, localFilePath = articleFile.absolutePath,
                    lastFetchedTimestamp = currentTime, revisionId = revisionId, categories = null
                )
                articleMetaDao.insert(newMeta)
                Log.i(TAG, "Successfully inserted new metadata for '$canonicalTitle'.")
            }
        }
    }
    
    suspend fun removeArticle(pageId: Int) {
        withContext(Dispatchers.IO) {
            val meta = articleMetaDao.getMetaByPageId(pageId)
            if (meta != null) {
                if (meta.localFilePath.isNotEmpty()) {
                    val file = File(meta.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                articleMetaDao.delete(meta)
            }
        }
    }

    fun isArticleOfflineFlow(pageId: Int): Flow<Boolean> {
        return articleMetaDao.getMetaByPageIdFlow(pageId).map { meta ->
            meta?.localFilePath?.isNotEmpty() == true && File(meta.localFilePath).exists()
        }
    }
}
