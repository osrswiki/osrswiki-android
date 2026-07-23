package {{PACKAGE_NAME}}

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.test.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testScope = TestScope()

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
    fun `initialState_isEmpty`() = runTest {
        // Assert initial state
        // TODO: Verify initial UI state
        // Example: assertEquals(UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `loadData_success_emitsSuccessState`() = runTest {
        // Arrange
        // TODO: Mock successful data loading
        // Example: whenever(mockRepository.getData()).thenReturn(flowOf(expectedData))
        
        // Act
        {{CLASS_INSTANCE}}.loadData()
        
        // Assert
        // TODO: Verify success state emission
        // Example: assertEquals(UiState.Success(expectedData), viewModel.uiState.value)
    }

    @Test
    fun `loadData_error_emitsErrorState`() = runTest {
        // Arrange
        // TODO: Mock error condition
        // Example: whenever(mockRepository.getData()).thenThrow(RuntimeException("Test error"))
        
        // Act
        {{CLASS_INSTANCE}}.loadData()
        
        // Assert
        // TODO: Verify error state emission
        // Example: assertTrue(viewModel.uiState.value is UiState.Error)
    }

    {{TEST_METHODS}}
}