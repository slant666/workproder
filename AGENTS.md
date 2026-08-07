# Cross-task continuity

## Start of a task

- Before working, check the current project root and working directory for `PROGRESS.md`.
- If it exists, read it completely before continuing. The user's latest instruction always has priority.
- Verify the actual files and workspace state instead of relying only on chat memory.
- Create and maintain `PROGRESS.md` for multi-step work, file changes, important decisions, or work likely to continue in another task.
- Do not create `PROGRESS.md` for simple questions or one-off read-only lookups.

## During work

- Update `PROGRESS.md` after a major milestone, important decision, direction change, or blocker.
- Keep valid information, remove stale temporary notes, and avoid copying a raw chat transcript.
- Never record passwords, tokens, private keys, or other secrets in the handoff file.

## Before finishing

- If `PROGRESS.md` exists, update it before the final response.
- For unfinished non-trivial work, record the current goal, completed work, key decisions and reasons, current state, next steps, blockers and risks, changed files, and verification results.
- If the work is fully complete, mark it complete and state that no required steps remain.

## Continuing in a new task

- When the user says to continue previous work, first read the current project's `PROGRESS.md` and related project documentation, then verify the workspace state.
- If no handoff file exists, or the directory contains multiple unrelated projects, do not mix contexts. Ask for the missing project path or context when necessary.
- Treat `PROGRESS.md` as a state summary, not as a substitute for source files, formal documentation, version history, or test results.
