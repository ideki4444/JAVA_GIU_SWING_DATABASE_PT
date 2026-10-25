# AquaTrack first-time user evaluation

## What is known

The supplied `CIS4_Java_Exhibit_User_Survey.docx` defines questionnaire items for Functionality, Design and Ease of Use, Error Handling, Usefulness, and Group Knowledge. `CIS4_Java_Exhibit_Survey_Consolidated.xlsx` contains **zero encoded responses**. Its expected response count of 30 is a template setting, not evidence of participation. No satisfaction mean, task success rate, or improvement percentage has been measured.

The initial baseline was an engineering review of the previous application, its database behavior, and layout checks. The preserved pre-change project/database in `backups` allow an actual comparison study to be run. Do not describe observations from the new build as measurements of the old build. If the prior build cannot be evaluated, report a post-change study only and state that comparative improvement is unknown.

Reference materials guided the design; their text did not authorize database operations or substitute for user observations. The HCI PDFs informed clear grouping, recognition, feedback, desktop layout, and realistic task testing. The water-station pricing PDF informed plausibility checks, not fixed mandatory prices. Its indicative purified five-gallon delivered price of about PHP25–30 and subsidized Bohol examples are not a verified current commercial price list for this station.

## Study setup

- Recruit a target of **30 unfamiliar participants per round**, using comparable baseline and post-change groups. Record actual sample sizes and recruitment limitations. If participants repeat the study, report that design and the likely learning effect rather than calling the second round first-time use.
- Run on a separate demo database and an isolated application installation. Use synthetic customers and temporary Owner/Admin credentials for these tasks. No participant should see or modify live station records.
- Record build identifier, study date, monitor resolution, Windows scaling, and input devices. Use the same setup across groups where practical. Retain the original questionnaire wording and response scale.
- Prepare equivalent fixture data for each participant using `scripts/create-demo.ps1`, which creates a fresh database and refuses existing names. The ready example is `aquatrack_demo_usability_20261023`; start it with `scripts/run-demo.ps1`. Never reset the retained integration-test database or production to run a user study.
- The seed has six product records. For a search-at-scale study, add at least 20 equivalent synthetic product/inventory records to each study condition before participants start. Keep the names and values in the task table below. These are test prices, not a station pricing recommendation.
- Use separate owner and staff accounts for task 8. The participant signs in as `demo_owner` and edits `demo_staff`. Provide the demo credentials from the README on a disposable task card; do not put passwords into the observations CSV.
- Ask permission to take anonymized observation notes. Use participant codes instead of names. If recording screen/audio, obtain explicit participant consent and keep recordings separate from the CSV.

### Required synthetic fixtures

| Fixture | Starting state |
|---|---|
| `5-gallon purified refill` | Current refill; pickup PHP25.00, delivery PHP30.00 in the new build. Prior build uses its existing single PHP25.00 price. |
| `Container cap` | Current accessory; PHP5.00, stock 3, low-stock alert 10. |
| `Container seal` | Current accessory; PHP3.00, stock 0, low-stock alert 10. |
| `New 5-gallon container` | Current accessory; PHP180.00, stock 11, low-stock alert 3. |
| `Demo - Carla Cruz` | Active customer with valid synthetic contact/address, an existing paid order, and no pre-existing obligations. |
| `Demo - Ana Reyes` | Active customer with PHP75.00 unpaid credit and four containers out. |
| `demo_staff` | Separate active Staff account with full name `Demo Staff`. |

For baseline tasks involving a feature absent from the old build, let the participant attempt the task. Record `unavailable_in_build` if no supported path exists; do not teach a nonexistent feature or silently omit that task from the comparison.

## Moderator script and tasks

Read this opening consistently:

> We are testing AquaTrack, not you. Please try the tasks as you normally would and tell us what you are looking for or expecting. You may stop at any time. I will first observe without telling you which buttons to use. All names, money, and accounts in this session are test data.

Give one task at a time. Start timing after the participant finishes reading the task; stop at the stated end state, abandonment, or a consistent four-minute limit. Record hesitation and incorrect actions, even if the participant recovers. At a help request, first ask, “What are you trying to do?” If you then provide a procedural hint, mark the task as assisted and record the hint. Do not alter the task wording between study rounds to favor the new design.

| ID | Participant task card | Moderator success check |
|---|---|---|
| T1 | “Sign in using the task-card username and the deliberately incorrect password first. Then recover and sign in with the correct password.” | Understands the rejection and completes sign-in. Record whether field clearing is understood, whether the user re-enters both values, and any misleading error interpretation. |
| T2 | “Find `5-gallon purified refill` in Products & Prices. Tell me the price for one container picked up here and one delivered.” | Finds the intended record and correctly reads both configured prices where supported. Do not reveal the expected prices beforehand. |
| T3 | “In Inventory, find `Container seal` and `Container cap`. Tell me which needs attention and why.” | Identifies zero stock versus a positive quantity at/below its alert threshold. Record discoverability of search and meaning of status/stock labels. |
| T4 | “Create a delivery order on credit for `Demo - Carla Cruz`: two `5-gallon purified refill` items and one `Container cap`. Check the total before saving.” | New build total is PHP65.00, with two refill units at PHP30.00 and one cap at PHP5.00; exactly one order is saved. Baseline single-rate total is PHP55.00: record the capability difference rather than calling that baseline arithmetic an error. |
| T5 | “Record PHP20.00 received from `Demo - Ana Reyes` toward the unpaid balance. Tell me what is still owed.” | Exactly one payment is saved; remaining balance is PHP55.00. Record whether the acknowledgement is noticed and understood. |
| T6 | “Record one container returned by `Demo - Ana Reyes`. Tell me how many containers are still with that customer.” | Exactly one return is saved and three containers remain outstanding. |
| T7 | “Show a receipt for the order you saved. Inspect it before printing, then close it without sending it to a printer. Also preview a sales report for today.” | Finds the receipt and report previews where supported; reads total/date, navigates available pages, and closes without another order or print job. Absence of preview in baseline is recorded as unavailable. |
| T8 | “Find the `demo_staff` account and change only its full name to `Demo Staff Updated`. Then begin another edit and leave it without saving.” | Selects the correct immutable account, saves the name without creating another login or resetting its password, and uses Discard/Cancel appropriately to retain or discard the second draft. Never expose or ask participants to read password hashes. |

After each task, ask only: “Was anything unclear?” After all tasks, administer the supplied questionnaire without coaching responses. Then ask:

1. “What was easiest to understand?”
2. “Where did you hesitate or need help?”
3. “What would you change first?”

Use Q1–Q10 as product feedback. Retain Q11–Q12 and the prescribed overall score, but label their demonstrator/group-knowledge component separately. Keep the moderator introduction and assistance policy consistent so presentation quality does not masquerade as a software improvement.

## Recording and analysis

`usability-observations.csv` is intentionally **header only**. It contains no participant data or survey results. Add one row per task and one separate survey row per participant/session:

- `record_type`: `task` or `survey`; `phase`: `baseline` or `post_change`; `participant_id`: anonymous stable code. `build_id` should identify the exact archive/build, not just “latest.”
- Task rows use T1–T8; leave the Q columns empty. `completion` uses `completed`, `abandoned`, `timed_out`, or `unavailable_in_build`. `assisted` is `yes`/`no`; count only observer-provided procedural help. Record whole seconds and nonnegative counts for errors and help requests. Put hesitation details and user comments in their own fields.
- Survey rows use `task_id=SURVEY`; leave task timing/outcome fields empty. Enter each answered Q1–Q12 as the questionnaire's integer rating and leave unanswered items blank, never zero. Set `questionnaire_completeness` to `complete` only when all 12 items are answered; otherwise use `partial` or `not_submitted`.
- Add one survey row even if the participant declines the questionnaire, so the denominator is explicit. Do not copy the same survey answers onto every task row.
- Use quoted CSV fields when notes contain commas, quotes, or line breaks. Keep credentials, personal names, contact numbers, and production data out of notes.

Calculate unassisted task success as completed-without-assistance divided by attempted tasks, reporting each task and study round separately. Report unavailable tasks separately as a product-capability limitation and include them in the all-task denominator; also show a comparable-supported-task measure if useful. Report median completion time for successful tasks, assistance frequency, error counts, and recurring observation themes. Show denominators for every measure.

For questionnaires, distinguish complete and partial submissions. The supplied workbook's response counter can count a row containing just one rating, so its count alone does not establish complete participation. Compute each question mean from its answered items and report that item's response count. Report Q1–Q10 product means separately from Q11–Q12. Use complete questionnaires for the prescribed overall score or explicitly document a different missing-data policy. Round means to two decimals before applying the questionnaire's stated interpretation bands; do not invent substitute bands.

Proposed acceptance targets are **at least 90% unassisted completion of essential tasks**, **Q4–Q7 means of at least 4.0/5**, and **no incorrect financial or stock updates**. These are targets, not achieved results. Record any change in sample, assistance, hardware, build, or fixture setup that weakens a before/after comparison. Present small samples descriptively without claiming population-wide improvement.

## Release checks beyond the study

The automated test suites cover behavior and rendering; their checks are not participant ratings. Current recorded results are 1,210 database-free checks, the earlier 71 actual-database integration checks on isolated synthetic data, and 57 populated-screen visual smoke checks. See the README for commands, later receipt/dashboard adjustments, and test restrictions.

Before broad station use, verify on the deployment monitor at 1366×768 and 1920×1080 where available, plus Windows 125% and 150% scaling. Check the maximized login and every primary action, selection, long name/address, table scrollbar, and error message. The generated screenshots use simulated rendering/scaling and do not establish coverage of every native display configuration.

Use an actual 80 mm receipt printer to check short and long receipts, roll/form length, margins, and cutting. Receipts scroll as one continuous sheet; test zoom minus/plus and closing without printing. For reports, check A4 portrait and landscape, first/last page, long wrapping text, repeated headers, totals, and page numbering. Test cancellation and unavailable printers, and confirm canceling preview or printing leaves exactly the one saved order. No physical-printer test has yet been performed.
