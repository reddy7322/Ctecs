package com.example

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MediaDeleteResult
import com.example.data.PhotoRecordDao
import com.example.data.PhotoRecordEntity
import com.example.data.PhotoRepository
import com.example.data.ScopedDeleteDelegate
import com.example.data.UriDeleteOutcome
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import com.example.ui.photos.PhotosScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GalleryMediaDeletionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoRecordDao

    private fun appPicturesDir(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        "HighwayCamera"
    ).apply { mkdirs() }

    private fun publicPicturesDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "HighwayCamera"
    ).apply { mkdirs() }

    private fun appMoviesDir(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
        "HighwayCamera"
    ).apply { mkdirs() }

    private fun publicMoviesDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "HighwayCamera"
    ).apply { mkdirs() }

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.photoRecordDao()

        appPicturesDir().deleteRecursively()
        publicPicturesDir().deleteRecursively()
        appMoviesDir().deleteRecursively()
        publicMoviesDir().deleteRecursively()
        File(context.filesDir, "HighwayCamera").deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        appPicturesDir().deleteRecursively()
        publicPicturesDir().deleteRecursively()
        appMoviesDir().deleteRecursively()
        publicMoviesDir().deleteRecursively()
        File(context.filesDir, "HighwayCamera").deleteRecursively()
    }

    private fun createPhotoEntity(
        fileName: String,
        filePath: String,
        thumbnailPath: String? = null,
        timestamp: Long = 1_750_000_000_000L,
        chainageLabel: String = "200+100"
    ): PhotoRecordEntity = PhotoRecordEntity(
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        fileName = fileName,
        timestamp = timestamp,
        latitude = 29.5,
        longitude = 77.7,
        accuracy = 3.0f,
        chainageMetres = 200100L,
        chainageLabel = chainageLabel,
        chainageConfidence = "HIGH",
        highwayId = "NH-58",
        roadSide = "LHS",
        lane = "M/L",
        activity = "BC"
    )

    @Test
    fun `first-time single photo deletion removes app copy, public copy, thumbnail, DB row and never resurrects on sync`() = runBlocking {
        val repo = PhotoRepository(context, dao)

        val targetName = "NH58--200+100-LHS_20260401_101500.jpg"
        val appCopy = File(appPicturesDir(), targetName).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val publicCopy = File(publicPicturesDir(), targetName).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val thumbCopy = File(context.cacheDir, "thumb_$targetName").apply { writeBytes(byteArrayOf(9, 8, 7)) }

        val keepName = "NH58--200+200-LHS_20260401_102000.jpg"
        val keepAppCopy = File(appPicturesDir(), keepName).apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
        val keepPublicCopy = File(publicPicturesDir(), keepName).apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }

        val targetId = dao.insertPhoto(createPhotoEntity(targetName, appCopy.absolutePath, thumbnailPath = thumbCopy.absolutePath))
        val keepId = dao.insertPhoto(createPhotoEntity(keepName, keepAppCopy.absolutePath, chainageLabel = "200+200"))
        val targetEntity = dao.getPhotoById(targetId)!!

        val result = repo.deletePhoto(targetEntity)
        assertTrue("Expected Success but got $result", result is MediaDeleteResult.Success)

        // Both storage copies + thumbnail must be deleted on first attempt
        assertFalse(appCopy.exists())
        assertFalse(publicCopy.exists())
        assertFalse(thumbCopy.exists())
        assertNull(dao.getPhotoById(targetId))

        // Unrelated photo must remain untouched
        assertTrue(keepAppCopy.exists())
        assertTrue(keepPublicCopy.exists())
        assertEquals(1, dao.getAllPhotosList().size)
        assertEquals(keepId, dao.getAllPhotosList().first().id)

        // Simulating Gallery reopen / app restart media sync must NOT resurrect deleted photo
        val synced = repo.syncMediaFilesFromStorage()
        assertEquals(0, synced)
        val allAfterSync = repo.allPhotos.first()
        assertEquals(1, allAfterSync.size)
        assertEquals(keepName, allAfterSync.first().fileName)
    }

    @Test
    fun `first-time video deletion removes app copy, public Movies copy, DB row and never resurrects on sync`() = runBlocking {
        val repo = PhotoRepository(context, dao)

        val videoName = "TimeVideo_20260401_110000.mp4"
        val appVideo = File(appMoviesDir(), videoName).apply { writeBytes(byteArrayOf(0, 0, 1, 2, 3)) }
        val publicVideo = File(publicMoviesDir(), videoName).apply { writeBytes(byteArrayOf(0, 0, 1, 2, 3)) }
        val internalVideo = File(File(context.filesDir, "HighwayCamera").apply { mkdirs() }, videoName).apply {
            writeBytes(byteArrayOf(0, 0, 1, 2, 3))
        }

        val id = dao.insertPhoto(createPhotoEntity(videoName, appVideo.absolutePath))
        val entity = dao.getPhotoById(id)!!

        val result = repo.deletePhoto(entity)
        assertTrue(result is MediaDeleteResult.Success)
        assertFalse(appVideo.exists())
        assertFalse(publicVideo.exists())
        assertFalse(internalVideo.exists())
        assertTrue(dao.getAllPhotosList().isEmpty())

        // Subsequent sync must not re-discover the deleted video
        val added = repo.syncMediaFilesFromStorage()
        assertEquals(0, added)
        assertTrue(repo.allPhotos.first().isEmpty())
    }

    @Test
    fun `duplicate database records for same photo are purged together on first deletion`() = runBlocking {
        val repo = PhotoRepository(context, dao)

        val name = "NH58--201+000_20260401_120000.jpg"
        val appCopy = File(appPicturesDir(), name).apply { writeBytes(byteArrayOf(10, 20, 30)) }
        val publicCopy = File(publicPicturesDir(), name).apply { writeBytes(byteArrayOf(10, 20, 30)) }

        val firstId = dao.insertPhoto(createPhotoEntity(name, appCopy.absolutePath))
        val dupId = dao.insertPhoto(createPhotoEntity(name, publicCopy.absolutePath))
        assertEquals(2, dao.getAllPhotosList().size)

        val result = repo.deletePhoto(dao.getPhotoById(firstId)!!)
        assertTrue(result is MediaDeleteResult.Success)
        assertNull(dao.getPhotoById(firstId))
        assertNull(dao.getPhotoById(dupId))
        assertTrue(dao.getAllPhotosList().isEmpty())
        assertFalse(appCopy.exists())
        assertFalse(publicCopy.exists())
    }

    @Test
    fun `bulk deletion deletes only selected items and preserves unselected items and fails honestly when URI cannot be removed`() = runBlocking {
        val blockedUri = Uri.parse("content://com.example.test.provider/document/protected_99")
        val fakeDelegate = object : ScopedDeleteDelegate {
            override fun deleteContentUri(uri: Uri): UriDeleteOutcome =
                if (uri == blockedUri) UriDeleteOutcome.Failed("Storage provider denied delete") else UriDeleteOutcome.NotFound

            override fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? = null
            override fun uriExists(uri: Uri): Boolean = uri == blockedUri
        }
        val repo = PhotoRepository(context, dao, fakeDelegate)

        val f1 = File(appPicturesDir(), "photo_1.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val f2 = File(appPicturesDir(), "photo_2.jpg").apply { writeBytes(byteArrayOf(3, 4)) }
        val keepFile = File(appPicturesDir(), "photo_keep.jpg").apply { writeBytes(byteArrayOf(5, 6)) }

        val id1 = dao.insertPhoto(createPhotoEntity("photo_1.jpg", f1.absolutePath))
        val id2 = dao.insertPhoto(createPhotoEntity("photo_2.jpg", f2.absolutePath))
        val blockedId = dao.insertPhoto(createPhotoEntity("protected.jpg", blockedUri.toString()))
        val keepId = dao.insertPhoto(createPhotoEntity("photo_keep.jpg", keepFile.absolutePath))

        val partialResult = repo.deleteMediaItems(
            listOf(dao.getPhotoById(id1)!!, dao.getPhotoById(id2)!!, dao.getPhotoById(blockedId)!!)
        )

        assertTrue("Expected PartialFailure but got $partialResult", partialResult is MediaDeleteResult.PartialFailure)
        val pf = partialResult as MediaDeleteResult.PartialFailure
        assertEquals(2, pf.deletedPhotos.size)
        assertEquals(1, pf.failedItems.size)
        assertEquals(blockedId, pf.failedItems.first().first.id)

        // Verified deleted items are gone from disk & DB; protected & unselected items remain
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        assertNull(dao.getPhotoById(id1))
        assertNull(dao.getPhotoById(id2))
        assertTrue(dao.getPhotoById(blockedId) != null)
        assertTrue(dao.getPhotoById(keepId) != null)
        assertTrue(keepFile.exists())
    }

    @Test
    fun `scoped storage approval flow retains item on denial and completes permanent removal on approval`() = runBlocking {
        val protectedUri = Uri.parse("content://media/external/images/media/4242")
        var osApprovedDeleted = false
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            Intent("com.example.ACTION_TEST_DELETE"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val fakeDelegate = object : ScopedDeleteDelegate {
            override fun deleteContentUri(uri: Uri): UriDeleteOutcome {
                if (uri != protectedUri) return UriDeleteOutcome.NotFound
                return if (osApprovedDeleted) {
                    UriDeleteOutcome.Deleted
                } else {
                    UriDeleteOutcome.RecoverableApprovalRequired(pendingIntent.intentSender)
                }
            }

            override fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? =
                if (protectedUri in uris) pendingIntent.intentSender else null

            override fun uriExists(uri: Uri): Boolean =
                uri == protectedUri && !osApprovedDeleted
        }

        val repo = PhotoRepository(context, dao, fakeDelegate)
        val appCopy = File(appPicturesDir(), "scoped_photo.jpg").apply { writeBytes(byteArrayOf(11, 22, 33)) }
        val publicCopy = File(publicPicturesDir(), "scoped_photo.jpg").apply { writeBytes(byteArrayOf(11, 22, 33)) }
        val id = dao.insertPhoto(createPhotoEntity("scoped_photo.jpg", protectedUri.toString()))
        val entity = dao.getPhotoById(id)!!

        // Step 1: Initial delete requests system approval without destroying local record
        val initialResult = repo.deleteMediaItems(listOf(entity))
        assertTrue("Expected RequiresSystemApproval but got $initialResult", initialResult is MediaDeleteResult.RequiresSystemApproval)
        val approvalReq = initialResult as MediaDeleteResult.RequiresSystemApproval
        assertTrue(dao.getPhotoById(id) != null)
        assertTrue(appCopy.exists())

        // Step 2: User denies system confirmation -> item & DB row remain completely intact
        repo.cancelPendingDeletion(approvalReq.targetPhotos)
        assertTrue(dao.getPhotoById(id) != null)
        assertTrue(appCopy.exists())
        assertTrue(publicCopy.exists())

        // Step 3: User retries and approves system confirmation -> MediaStore URI + copies + DB row are all gone
        val retryReq = repo.deleteMediaItems(listOf(entity)) as MediaDeleteResult.RequiresSystemApproval
        osApprovedDeleted = true
        publicCopy.delete() // simulates Android OS deleting the MediaStore-backed file upon approval
        val finalResult = repo.completeApprovedDeletion(retryReq.targetPhotos, retryReq.pendingUris)

        assertTrue("Expected Success after approval but got $finalResult", finalResult is MediaDeleteResult.Success)
        assertNull(dao.getPhotoById(id))
        assertFalse(appCopy.exists())
        assertFalse(publicCopy.exists())
        assertEquals(0, repo.syncMediaFilesFromStorage())
        assertTrue(repo.allPhotos.first().isEmpty())
    }

    @Test
    fun `PhotosScreen Grid and List views immediately remove deleted photo and persist deletion across sync`() {
        val fileName = "NH58--205+500-RHS_20260401_150000.jpg"
        val appCopy = File(appPicturesDir(), fileName).apply { writeBytes(byteArrayOf(1, 3, 5, 7, 9)) }
        val publicCopy = File(publicPicturesDir(), fileName).apply { writeBytes(byteArrayOf(1, 3, 5, 7, 9)) }

        val appDao = AppDatabase.getDatabase(context).photoRecordDao()
        val insertedId = runBlocking {
            appDao.getAllPhotosList().forEach { appDao.deletePhotoById(it.id) }
            appDao.insertPhoto(
                createPhotoEntity(fileName, appCopy.absolutePath, chainageLabel = "205+500")
            )
        }

        val viewModel = MainViewModel(application)
        viewModel.updateGalleryPrefs(viewMode = "GRID")

        composeTestRule.setContent {
            MaterialTheme {
                PhotosScreen(viewModel = viewModel)
            }
        }
        // Wait for async Room Flow to deliver inserted photo into Grid View
        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_card_$insertedId")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("photo_card_$insertedId").assertIsDisplayed()

        // Switch to List View and verify item is shown there too before deletion
        viewModel.updateGalleryPrefs(viewMode = "LIST")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("205+500")).fetchSemanticsNodes().isNotEmpty()
        }

        // Switch back to Grid View and perform delete via ViewModel (single confirmation flow)
        viewModel.updateGalleryPrefs(viewMode = "GRID")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_card_$insertedId")).fetchSemanticsNodes().isNotEmpty()
        }

        val target = runBlocking { appDao.getPhotoById(insertedId)!! }
        viewModel.deletePhoto(target)

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { appDao.getPhotoById(insertedId) == null }
        }
        composeTestRule.waitForIdle()

        // Card disappears immediately from Grid View & List View, and both disk copies are gone
        composeTestRule.onNodeWithTag("photo_card_$insertedId").assertDoesNotExist()
        viewModel.updateGalleryPrefs(viewMode = "LIST")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("photo_card_$insertedId").assertDoesNotExist()
        assertFalse(appCopy.exists())
        assertFalse(publicCopy.exists())

        // Re-running storage sync (simulating revisiting Gallery / app restart) never restores item
        val syncedCount = runBlocking { viewModel.photoRepository.syncMediaFilesFromStorage() }
        assertEquals(0, syncedCount)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("photo_card_$insertedId").assertDoesNotExist()
        assertEquals(ToastType.SUCCESS, viewModel.toastMessage.value?.type)

        // Clean singleton DB table
        runBlocking {
            appDao.getAllPhotosList().forEach { appDao.deletePhotoById(it.id) }
        }
    }
}
