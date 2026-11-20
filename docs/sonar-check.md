# SonarCloud findings: what is left to handle

Tracking list for issue #65. Each open finding is assigned to one of three outcomes:

1. **Fixed by a pending branch**: closes when that branch is merged and analysed.
2. **Code change**: still to be done in the code.
3. **Mark in SonarCloud**: leave the code as it is and resolve the finding in SonarCloud as
   *Won't fix* or *False positive*, with the reason given here.

Snapshot: SonarCloud analysis of `master` at `1e3e2b2` (after PR #83), **70 open code smells**.
No bugs, no vulnerabilities, nothing critical. File paths are relative to
`oas-buddy-*/src/{main,test}/java/no/maddin/oasbuddy/`, and line numbers are those of that analysis.

Query: `https://sonarcloud.io/api/issues/search?componentKeys=oas-buddy_oas-buddy&resolved=false&ps=200`

| Outcome | Findings |
|---|---|
| 1. Fixed by pending branch `refactor/sonar-mechanical-2` | 15 |
| 2. Code change | 31 |
| 3. Mark in SonarCloud | 24 |
| **Total** | **70** |

---

## 1. Fixed by a pending branch (15)

Branch `refactor/sonar-mechanical-2` (commit `7c6a3a2`), not yet pushed. Full suite green.

| Rule | # | Where | Change |
|---|---|---|---|
| `S4276` | 5 | `FormFields` 152, 171 · `PathItemPane` 45 · `SchemaDetails` 140 · `SchemaPane` 74 | `BooleanSupplier`, `UnaryOperator<String>`, `BiPredicate<String, String>` |
| `S5411` | 2 | `PathItemPane` 52 · `SchemaPane` 82 | Cleared by the `BiPredicate` change above |
| `S7467` | 5 | `Constraint` 136 · `SchemaValues` 52 · `OasValidator` 45 · `ReferenceCheck` 92 · `AppInfo` 56 | Unused `catch` variable → `_` |
| `S5785` | 3 | `NamedObjectsTest` 38 · `SchemaPaneValuesTest` 188 → `assertNotNull`; `ParametersTest` 93 → `assertSame` | |

---

## 2. Code change (31)

Ordered from most to least worthwhile.

**Status:** every *medium* (`MAJOR`) finding here — 2a, 2c, 2d, 2e (23, plus the `S9435` on the same
line as an `S3358`) — is fixed on branch `refactor/sonar-medium`. Only 2b (`S1130`, minor) remains.

### 2a. Regex with super-linear backtracking — `S8786` (2) — *do this*

The only findings with a possible runtime effect. Both split comma-separated user input with
`"\\s*,\\s*"`; on a long run of spaces without a comma the two `\s*` can backtrack against each other.

| Where | Code |
|---|---|
| `desktop/pane/SchemaPane.java` 112 | `value.split("\\s*,\\s*")` |
| `desktop/pane/ServersEditor.java` 179 | `text.split("\\s*,\\s*")` |

Fix: split on `","` and `strip()` each part, for example
`Arrays.stream(text.split(",")).map(String::strip)…`. If both read a comma list the same way, use one
shared helper in `FormFields`.

### 2b. Unnecessary `throws` — `S1130` (8) — *mechanical*

Fallout from PR #83: the four `assert…RoundTrip(DocumentFormat)` helpers no longer throw, so their
callers' `throws IOException` is now unnecessary too.

| Where |
|---|
| `core/document/DocumentRoundTripTest.java` 96, 101, 124, 129, 156, 161, 192, 197 |

Fix: remove the `throws IOException` from those eight test methods.

### 2c. Nested ternaries — `S3358` (10) + `S9435` (1)

**Six of them are one pattern**, the rename check passed to `FormFields.renameField`:

```java
names.contains(candidate)
        ? "A <noun> named \"" + candidate + "\" already exists."
        : rename(old, candidate) ? null : "";
```

| Where |
|---|
| `desktop/pane/ReferableList.java` 196 |
| `desktop/pane/CallbackExpressions.java` 76 |
| `desktop/pane/LinkForm.java` 93 |
| `desktop/pane/ServersEditor.java` 146 |
| `desktop/pane/PathItemPane.java` 52 |
| `desktop/pane/SchemaPane.java` 82 |

Fix: one helper in `FormFields`, for example
`renameAttempt(Collection<String> taken, String collisionMessage, Predicate<String> rename)`, which
returns the message on a collision, `null` when `rename` succeeds, and `""` when it is declined. This
removes the duplication as well as the six findings.

**The other four, one at a time:**

| Where | Code | Suggested fix |
|---|---|---|
| `desktop/pane/OperationPane.java` 124–125 (`S9435` + `S3358`) | `!security.isDeclared() ? INHERIT : …isEmpty() ? PUBLIC : REQUIRE` | A small `securityMode(security)` method with early returns |
| `desktop/pane/SchemaPane.java` 53 | `allowed == null ? UNSPECIFIED : allowed ? ANY : NONE` | `if (allowed == null) return UNSPECIFIED;` then a plain ternary |
| `desktop/pane/SchemaConstraints.java` 84 | `current == null ? "" : current.isTextual() ? current.asText() : current.toString()` | A `display(JsonNode)` helper |
| `desktop/pane/ExamplesEditor.java` 56 | `component == null ? "(not declared)" : summary == null ? "" : summary` | `component == null ? "(not declared)" : FormFields.nullToEmpty(component.getSummary())` |

### 2d. String concatenation → text block — `S6126` (10) — *tests*

The removal tests build the expected confirmation text with `+ "\n"`. A text block reads closer to
what the dialog shows.

| Where (`desktop/pane/…Test.java`) |
|---|
| `CallbackRemovalTest` 29 · `ExampleRemovalTest` 29 · `HeaderRemovalTest` 29 · `LinkRemovalTest` 29 |
| `ParameterRemovalTest` 31 · `ResponseRemovalTest` 30 · `TagRemovalTest` 50 · `SecuritySchemeRemovalTest` 52 |
| `SchemaRemovalTest` 69, 81 |

Watch out: the expected strings have no trailing newline, so close the text block with `"""` on the
last content line, not on a line of its own.

### 2e. Small renames (2)

| Rule | Where | Fix |
|---|---|---|
| `S1117` | `desktop/pane/SchemaConstraints.java` 75 | The local `CheckBox box` hides the field `box` (line 35). Rename the local to `checkBox`. |
| `S6213` | `desktop/LoggingProviderTest.java` 42 | Parameter `record` in `publish(LogRecord record)` is a restricted identifier. Rename it to `logRecord`. |

---

## 3. Mark in SonarCloud (24)

Resolve each finding in SonarCloud (*Issues → select → Resolve as …*) and paste the reason as a
comment.

### `S5411` boxed Boolean as condition (13) → *False positive*

> The value is the `Boolean` parameter of a JavaFX `ChangeListener` on a boolean property
> (`focusedProperty`, `selectedProperty`), or a value already tested non-null. It is never null. The
> only accepted fix, `Boolean.TRUE.equals(x)`, guards against nothing and reads worse.

| Where (`desktop/pane/`) |
|---|
| `CallbackExpressions` 81 · `FormFields` 191 · `HeaderForm` 26 · `LinkForm` 98 |
| `OAuthFlowsPane` 80, 172 · `OperationPane` 164, 246 · `ReferableList` 201 |
| `SchemaConstraints` 80 · `SecurityRequirementsPane` 127 · `ServersEditor` 150, 232 |

### `S1172` unused method parameter (3) → *False positive*

> These are hook methods with a default body. Subclasses override them and use the parameters
> (`ExamplesEditor.resolved`, `CallbacksEditor.below`/`mayRemove`). Removing the parameters would
> break the overrides.

| Where |
|---|
| `desktop/pane/ReferableList.java` 63, 68, 73 |

### `S3415` assertion argument order (2) → *False positive*

> The test pins the value of a constant, so the literal list is the expected value and the constant
> is the actual value. The order is correct.

| Where |
|---|
| `core/model/OAuthFlowsTest.java` 25 (`OAuthFlows.FLOW_NAMES`) |
| `core/model/SecuritySchemeTest.java` 130 (`SecurityScheme.EDITABLE_TYPES`) |

### `S6206` class → record (1) → *False positive*

> Facade classes stay plain classes on purpose: a record would expose the raw `ObjectNode` as a
> public accessor and give value-based `equals`, whereas the facade relies on node identity. The
> other five `S6206` findings (`Callback`, `Link`, `ApiResponse`, `Parameter`, `Server`) are already
> resolved this way.

| Where |
|---|
| `core/model/Example.java` 11 |

### `S5976` merge into a parameterized test (2) → *Won't fix*

> Each test names the case it covers and carries its own explanation (a Javadoc comment, a distinct
> fixture or a distinct expected message). Tests are kept readable as a specification; merging them
> into one parameterized test would lose that for no behavioural gain.

| Where |
|---|
| `core/validation/OasValidatorTest.java` 18 (three "fixture has no errors" tests) |
| `core/validation/ReferenceCheckTest.java` 21 (dangling schema/response/header references, which differ in expected outcome) |

### `S107` too many parameters (1) → *Won't fix* (or a code change, if preferred)

> `modeRadio` threads the operation's security context (group, mode, label, current mode, action,
> operation, box, catalog) into one radio button. A parameter object would only exist to satisfy the
> count.

| Where |
|---|
| `desktop/pane/OperationPane.java` 156 |

If you would rather fix it in code: introduce a small record (for example
`SecurityContext(Operation, VBox, SecuritySchemeCatalog)`) that the three security-radio helpers share.
