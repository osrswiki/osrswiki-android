package com.omiyawaki.osrswiki.savedpages

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedPageSyncForegroundTest {
    @Test
    fun emptyQueueDoesNotPromoteButEitherExplicitQueueDoes() {
        assertNull(SavedPageSyncForegroundPolicy.plan(0, 0))
        assertEquals(2, SavedPageSyncForegroundPolicy.plan(2, 0)?.totalItems)
        assertEquals(3, SavedPageSyncForegroundPolicy.plan(0, 3)?.totalItems)
        assertEquals(5, SavedPageSyncForegroundPolicy.plan(2, 3)?.totalItems)
    }

    @Test
    fun foregroundInfoIsQuietCancellableDataSyncWithBoundedProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.omiyawaki.osrswiki.TEST_CANCEL").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val info = SavedPageSyncForegroundInfoFactory(context).create(
            completedItems = 2,
            totalItems = 5,
            cancelIntent = cancelIntent
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        assertEquals(5, info.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(2, info.notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(info.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(info.notification.actions.isNullOrEmpty())
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(SavedPageSyncForegroundInfoFactory.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun initialAndDeleteOnlyProgressOmitsMisleadingCancelAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = SavedPageSyncForegroundInfoFactory(context).create(
            completedItems = 0,
            totalItems = 2,
            cancelIntent = null
        )

        assertTrue(info.notification.actions.isNullOrEmpty())
    }

    @Test
    fun notificationCancelCarriesExactActivePageToAppOwnedReceiver() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = SavedPageSyncCancelReceiver.intent(context, 9_876_543_210L)

        assertEquals(
            SavedPageSyncCancelReceiver::class.java.name,
            intent.component?.className
        )
        assertEquals(9_876_543_210L, SavedPageSyncCancelReceiver.pageIdFrom(intent))
        assertNull(SavedPageSyncCancelReceiver.pageIdFrom(Intent("unrelated")))
    }

    @Test
    fun workerAndManifestKeepPromotionScopedAndTarget36Compliant() {
        val worker = File(
            "src/main/java/com/omiyawaki/osrswiki/savedpages/SavedPageSyncWorker.kt"
        ).readText()
        val snapshot = File(
            "src/main/java/com/omiyawaki/osrswiki/savedpages/ReadingListPageSnapshot.kt"
        ).readText()
        val readingListDao = File(
            "src/main/java/com/omiyawaki/osrswiki/readinglist/db/ReadingListPageDao.kt"
        ).readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val queueRead = worker.indexOf("getPagesToProcessForDeleting()")
        val promotion = worker.indexOf("SavedPageSyncForegroundPolicy.plan")
        val saveProcessing = worker.indexOf("processPagesToSave(pagesToSave)")

        assertTrue(queueRead in 0 until promotion)
        assertTrue(promotion in 0 until saveProcessing)
        assertTrue(worker.contains("setProgress(progress)"))
        assertFalse(worker.contains("createCancelPendingIntent"))
        assertTrue(worker.contains("SavedPageSyncCancelReceiver.pendingIntent"))
        assertTrue(worker.contains("foregroundCancelableSavePageId = page.id"))
        assertTrue(worker.contains("cancelUniqueWork(UNIQUE_WORK_NAME)"))
        assertTrue(worker.contains("SavedPageSyncCancellationReconciler.reconcile"))
        assertTrue(worker.contains("ReadingListSnapshotPublicationHandoff.publishOrNull"))
        assertTrue(snapshot.contains("database.withTransaction"))
        assertTrue(snapshot.contains("publisher.publish(publication, currentTimeMs).also { stage.markPublished() }"))
        assertTrue(snapshot.contains("ReadingListSnapshotOwnershipLostException(pageId: Long)"))
        assertTrue(snapshot.contains("SavedPageSyncStatusRecorder.markSaveSuccess"))
        assertTrue(readingListDao.contains("durableSettlementVersion = :settlementVersion"))
        assertTrue(worker.contains("SavedPageSyncStatusRecorder.markSaveFailure"))
        assertTrue(worker.contains("updateQueuedSaveProgressOrCancel"))
        assertTrue(worker.contains("catch (ownershipLost: ReadingListSnapshotOwnershipLostException)"))
        assertFalse(worker.contains("readingListPageDao.updatePageStatusToSavedAndMtime"))
        assertFalse(worker.contains("readingListPageDao.updatePageDownloadProgress"))
        assertTrue(worker.contains("catch (cancellation: CancellationException)"))
        assertTrue(worker.contains("catch (linkageError: LinkageError)"))
        assertTrue(worker.contains("pagesToSave.forEach { page ->"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifest.contains(".savedpages.SavedPageSyncCancelReceiver"))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }
}
