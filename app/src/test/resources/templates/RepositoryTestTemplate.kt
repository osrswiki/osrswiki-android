package {{PACKAGE_NAME}}

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(MockitoJUnitRunner::class)
class {{CLASS_NAME}}Test {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    {{MOCK_DEPENDENCIES}}

    private lateinit var {{CLASS_INSTANCE}}: {{CLASS_NAME}}

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        {{CLASS_INSTANCE}} = {{CONSTRUCTOR_CALL}}
    }

    @Test
    fun `constructor_validParameters_createsInstance`() {
        // Arrange & Act
        // Instance created in setUp()
        
        // Assert
        assertNotNull({{CLASS_INSTANCE}})
    }

    @Test
    fun `getData_cacheHit_returnsFromCache`() = runTest {
        // Arrange
        // TODO: Set up cache with data
        // Example: whenever(mockLocalDataSource.getData()).thenReturn(flowOf(cachedData))
        
        // Act
        val result = {{CLASS_INSTANCE}}.getData()
        
        // Assert
        // TODO: Verify cache was used
        // Example: verify(mockLocalDataSource).getData()
        // verify(mockRemoteDataSource, never()).getData()
    }

    @Test
    fun `getData_cacheMiss_fetchesFromRemote`() = runTest {
        // Arrange
        // TODO: Set up empty cache and mock remote
        // Example: whenever(mockLocalDataSource.getData()).thenReturn(flowOf(emptyList()))
        // whenever(mockRemoteDataSource.getData()).thenReturn(flowOf(remoteData))
        
        // Act
        val result = {{CLASS_INSTANCE}}.getData()
        
        // Assert
        // TODO: Verify remote fetch and cache update
        // Example: verify(mockRemoteDataSource).getData()
        // verify(mockLocalDataSource).saveData(remoteData)
    }

    @Test
    fun `getData_networkError_propagatesError`() = runTest {
        // Arrange
        // TODO: Mock network error
        // Example: whenever(mockRemoteDataSource.getData()).thenThrow(IOException("Network error"))
        
        // Act & Assert
        // TODO: Verify error handling
        // Example: assertFailsWith<IOException> { repository.getData().first() }
    }

    @Test
    fun `refreshData_success_updatesCache`() = runTest {
        // Arrange
        // TODO: Mock successful remote fetch
        
        // Act
        {{CLASS_INSTANCE}}.refreshData()
        
        // Assert
        // TODO: Verify cache update
    }

    {{TEST_METHODS}}
}