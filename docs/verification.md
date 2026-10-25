# Verification and import provenance

The authoritative input is the uploaded `Aquatrack3-gpt-claude_1.zip`, corresponding to the user's explicitly selected NetBeans folder. The repository branch starts after the previous wrong-project import was reverted. No old AquaTrack project is restored.

## Import decisions

Java source, tests, scripts, libraries and shared NetBeans metadata are byte-identical to the upload. The project README is replaced with a link to the new learning guide. Database backups, the data-bearing portable SQL dump, old JAR/ZIP backups, compiled build/dist folders and private IDE settings are excluded. The clean schema is derived only from CREATE/ALTER statements; auto-increment history is removed. The migration is extracted from the upload's appended SQL. A schema-only pre-migration variant and guarded first-owner script complete the supplied helpers' missing inputs.

The supplied usability protocol and blank observations CSV are retained under the project’s docs/ folder as historical reference material. Their mentions of survey files, private backups and pre-created local databases do not imply those resources are included or available here. No participant results are claimed.

The uploaded README refers to previous installations, prepared demo databases, 1,210 checks and a prior packaged JAR hash. Those are historical claims, not results reproduced by this import. They are not advertised as current verification.

## Current checks

Compiled all application and test sources using JDK 26.0.2 with `--release 25`. Ran exactly the thirteen database-free programs listed by `scripts/build.ps1` on that runtime in headless Linux. Twelve passed; one failed. No Java source/test changes were made to hide the failure.

| Program | Result |
| --- | --- |
| `aquatrack.ui.FixedSplitLayoutTest` | FAIL — expected sidebar 400, source specifies 420 |
| `aquatrack.ui.DashboardLayoutTest` | PASS — 94 checks |
| `aquatrack.ui.LoginAndThemeTest` | PASS — 42 checks |
| `aquatrack.ui.WorkspaceViewportTest` | PASS — 34 checks |
| `aquatrack.ui.ProductInventoryFlowTest` | PASS — 29 checks |
| `aquatrack.ui.OrderPricingFlowTest` | PASS — 21 checks |
| `aquatrack.dao.OrderTransactionTest` | PASS — 28 checks |
| `aquatrack.dao.LedgerAccountsTransactionTest` | PASS — 123 checks |
| `aquatrack.ui.LedgerAccountsUiTest` | PASS — 43 checks |
| `aquatrack.util.PrintDocumentTest` | PASS — 536 checks |
| `aquatrack.ui.PrintPreviewFormatTest` | PASS — 11 checks |
| `aquatrack.ui.ReceiptPreviewTest` | PASS — 14 checks |
| `aquatrack.ui.ReportPrintSnapshotTest` | PASS — 20 checks |

`OrderPanel.java` configures `UiKit.splitFixedLeft(details, items, 420)`. `FixedSplitLayoutTest` expects 400 for that screen and fails with “OrderPanel hidden before first visit: expected 400, got 420”. Decide the intended width before changing implementation or assertion. The PowerShell `-Test` path aborts on the first failed test and therefore does not package afterward; omit `-Test` for a build-only package. The other twelve programs were run independently here to expose their results.

Fontconfig also reported non-writable cache directories; this did not stop the passing programs. The source/test width mismatch is explicit and is not attributed to fonts.

## Not exercised

No live MySQL/MariaDB server integration, Windows PowerShell execution, NetBeans/Ant build, visible desktop session, physical thermal printer, or participant study was run. `MySqlIntegrationTest` and `ScreenVisualSmokeTest` require an explicitly prepared isolated database and were not run. New setup SQL was reviewed structurally, not executed against a live server. Deployment should validate that SQL and the target printer/monitor before live use.

The repository includes deterministic SVG generation and a PDF exporter. Local link targets, SVG XML, PDF text/page bounds and rendered figures are checked during documentation preparation. PDF layout depends on installed fonts and renderer versions; byte-identical PDF output across operating systems is not promised.
