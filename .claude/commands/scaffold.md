# Scaffold Command

Generate comprehensive test files for existing classes with intelligent platform detection and coverage analysis.

## Usage
```bash
/scaffold [ClassName]
```

If no class name is provided, Claude will analyze the current session context and suggest classes that need tests.

## Platform Detection

Claude automatically detects the target platform:

1. **Check `.claude-platform` file** if it exists (created by `/start`)
2. **Check for active session files**:
   - `.claude-session-device` exists → Android platform
   - `.claude-session-simulator` exists → iOS platform  
   - Both exist → Cross-platform session
3. **If unclear**, ask: "Which platform would you like to scaffold tests for: Android, iOS, or both?"

## Android Test Scaffolding

### Unit Test Generation
For a given class (e.g., `SearchViewModel`):

1. **Analyze the class structure**:
   ```bash
   # From worktree session directory:
   # Find the class file
   find platforms/android -name "*SearchViewModel.kt" -type f
   ```

2. **Create corresponding test file**:
   ```bash
   # From worktree session directory:
   ./scripts/android/scaffold-unit-test.sh "SearchViewModel"
   ```

3. **Generate test structure** including:
   - Proper imports and annotations
   - Test class with InstantTaskExecutorRule and MainDispatcherRule
   - Mock dependencies using Mockito
   - Test methods for public functions
   - Arrange/Act/Assert pattern
   - Success and failure scenarios
   - Edge cases and boundary conditions

### Instrumented Test Generation
For UI components (Activities, Fragments, ViewHolders):

1. **Create UI test file**:
   ```bash
   # From worktree session directory:
   ./scripts/android/scaffold-ui-test.sh "SearchActivity"
   ```

2. **Generate UI test structure** including:
   - Espresso test setup
   - ActivityScenarioRule or FragmentScenario
   - UI interaction tests
   - State verification tests
   - Navigation flow tests

### Coverage Gap Analysis
Identify untested classes and methods:

1. **Analyze coverage gaps**:
   ```bash
   # From worktree session directory:
   ./scripts/android/analyze-coverage-gaps.sh
   ```

2. **Generate prioritized test list**:
   - Critical business logic (ViewModels, Repositories)
   - UI components with complex interactions
   - Utility classes with edge cases
   - Network and database layers

## iOS Test Scaffolding

### Unit Test Generation
For iOS classes (ViewModels, Services, Utilities):

1. **Create XCTest file**:
   ```bash
   # From worktree session directory:
   ./scripts/ios/scaffold-unit-test.sh "SearchViewModel"
   ```

2. **Generate test structure** including:
   - Proper imports and test class setup
   - Mock dependencies
   - Test methods for public functions
   - Arrange/Act/Assert pattern
   - Async testing with expectations

### UI Test Generation
For iOS UI components:

1. **Create UI test file**:
   ```bash
   # From worktree session directory:
   ./scripts/ios/scaffold-ui-test.sh "SearchViewController"
   ```

2. **Generate UI test structure** including:
   - XCUITest setup
   - UI element interactions
   - State verification
   - Navigation testing

## Test Templates and Patterns

### Android Test Template Structure
```kotlin
class ClassNameTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    
    @Mock lateinit var mockDependency: DependencyType
    private lateinit var classUnderTest: ClassName
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        classUnderTest = ClassName(mockDependency)
    }
    
    @Test
    fun `methodName_normalCondition_expectedResult`() {
        // Arrange
        // Act
        // Assert
    }
    
    @Test
    fun `methodName_errorCondition_expectedErrorHandling`() {
        // Arrange
        // Act
        // Assert
    }
}
```

### iOS Test Template Structure
```swift
class ClassNameTests: XCTestCase {
    var classUnderTest: ClassName!
    var mockDependency: MockDependencyType!
    
    override func setUp() {
        super.setUp()
        mockDependency = MockDependencyType()
        classUnderTest = ClassName(dependency: mockDependency)
    }
    
    func testMethodName_normalCondition_expectedResult() {
        // Arrange
        // Act
        // Assert
    }
    
    func testMethodName_errorCondition_expectedErrorHandling() {
        // Arrange
        // Act
        // Assert
    }
}
```

## Interactive Workflows

### Quick Scaffolding
```bash
# Generate test for specific class
/scaffold SearchViewModel

# Analyze coverage and suggest next tests
/scaffold --analyze

# Generate tests for entire package
/scaffold --package com.omiyawaki.osrswiki.search
```

### Comprehensive Coverage Drive
```bash
# From worktree session directory:
# 1. Analyze current coverage
./scripts/android/analyze-coverage-gaps.sh

# 2. Generate tests for critical gaps
/scaffold --priority-gaps

# 3. Run tests to verify coverage improvement
/test

# 4. Iterate until 65% threshold met
```

## Success Criteria

### Android
- Test file created in `src/test/java/` or `src/androidTest/java/`
- Proper imports and annotations included
- Mock dependencies set up correctly
- Multiple test scenarios covered
- Follows project testing patterns

### iOS
- Test file created in appropriate test target
- XCTest setup configured properly
- Mock dependencies implemented
- Multiple test scenarios covered
- Async testing handled correctly

### Quality Gates
- Generated tests compile without errors
- Tests run successfully (even if initially failing)
- Coverage percentage improves after test creation
- Code follows established testing patterns

## Integration with Existing Workflow

1. **Use after feature development**: Generate tests for new classes
2. **Use before refactoring**: Create safety net of tests
3. **Use for legacy code**: Add tests to existing untested classes
4. **Use with /test command**: Generate tests, then run them

This command bridges the gap between "run tests" and "write tests" by providing intelligent test generation that actually creates the tests needed to meet your 65% coverage requirement.