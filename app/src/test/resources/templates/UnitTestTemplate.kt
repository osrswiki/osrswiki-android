package {{PACKAGE_NAME}}

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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

    {{TEST_METHODS}}
}