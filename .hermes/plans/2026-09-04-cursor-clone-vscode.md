# Cursor-style VS Code Layout for Tape

Date: 2026-09-04
Repo: /Users/akshaysharma/Downloads/Work/Projects/Tape
User: Akshay (Caesar); editor agent: Marc
Status: Plan — implemented end-to-end below.

## Goal

Make VS Code look and feel like Cursor for the Tape project:

- File tree left, editor center, Claude Code right (sidebar), Hermes bottom (integrated terminal)
- One-keystroke summon for both Claude (⌘L) and Hermes (⌘I)
- Project context files so both agents know the project the moment they open
- Editor polish (minimap off, soft scrollbar, format-on-save, autosave)

## What Cursor ships that we are NOT replicating

Cursor's main moat is the inline multi-file diff composer (⌘K → multi-file edit, side-by-side
diff, agent running edits across files). VS Code has no equivalent — `claude-code` extension
does inline edits but the UX is not byte-identical. This plan gets the **shape** and the
**feel** right; the last 30% is a Cursor-license product decision, not a settings.json one.

## Scope — IN

1. `.vscode/settings.json` extensions:
   - `terminal.integrated.profiles.osx` with a Hermes profile that runs Hermes directly
   - `terminal.integrated.defaultProfile.osx` -> Hermes (replaces bash on terminal open)
   - `workbench.sideBar.location: right` (already Claude panel position)
   - `editor.minimap.enabled: false`
   - `editor.scrollbar.vertical: "auto"` (hidden unless scrolling)
   - `editor.scrollbar.horizontal: "auto"`
   - `files.autoSave: "afterDelay"`
   - `editor.formatOnSave: true`
   - `editor.smoothScrolling: true`
   - `editor.cursorBlinking: "smooth"`
   - panel position locked to bottom via existing `workbench.panel.defaultLocation`
   - `terminal.integrated.tabs.enabled: true`

2. `.vscode/keybindings.json` (new):
   - `⌘L` → workbench.action.focusAuxiliaryBar (Claude panel)
   - `⌘⇧L` → workbench.panel.chat.view.focus (or `claude.newChat` if extension defines one)
   - `⌘I` → workbench.action.togglePanel (Hermes terminal)
   - `⌘K ⌘T` → terminal.new with profile `Hermes` (new Hermes session)

3. `.vscode/CLAUDE.md` (new):
   - Project pointers — what Tape is, the design principles, the build commands, the key files
   - Same pointer set Claude Code extension auto-loads

4. `.hermes.md` (new, repo root):
   - Same project pointers for Hermes (Hermes reads `.hermes.md` in priority over
     `CLAUDE.md`; first match wins per the hermes-agent skill)
   - Repo is currently NOT a git repo (`git status` → "fatal: not a git repository").
     Until git is initialized the parent walk stops at filesystem root anyway. Both files
     are written anyway — no harm, only loading redundancy.

## Scope — OUT

- Hermes command set (slash commands like `/goal`, `/compress`) — terminal agent behavior,
  not an editor setting
- Claude Code extension's internal settings — extension provides its own, do not override
- macOS-only platform split for keyboard — write `keybindings.json` neutrally, the
  command IDs are cross-platform
- A dedicated `.vscode/.hermes/instructions.md` — Hermes reads `.hermes.md` directly; the
  nested instructions convention does not apply (verified via hermes-agent skill, "Pick the
  right one" table)

## Files touched

| Path                                           | Action | Why |
|------------------------------------------------|--------|-----|
| `.vscode/settings.json`                        | edit   | Add terminal profiles + editor polish |
| `.vscode/keybindings.json`                     | create | ⌘L / ⌘⇧L / ⌘I / ⌘K⌘T |
| `.vscode/CLAUDE.md`                            | create | Project context for Claude Code |
| `.hermes.md`                                   | create | Project context for Hermes |
| `.hermes/plans/2026-09-04-cursor-clone-vscode.md` | create | This file |

## Decisions locked

- **Hermes launcher command:** `hermes chat` (the default interactive surface). Not
  `hermes -p` (print mode exits; defeats the purpose of a persistent terminal).
  User can re-bind by editing the profile path.
- **Default terminal profile:** Hermes, NOT bash — so opening the panel gives you the
  agent, not a shell. Bash is still available as the "Default" profile via the dropdown.
- **Editor polish:** `editor.minimap.enabled: false` is non-negotiable for the Cursor
  feel. Scrollbar `auto` is the matching setting.
- **No editor.tokenColorCustomizations:** the warm-amber TextMate theme is the source of
  truth for syntax colors. `colorCustomizations` is for workbench chrome only — adding it
  here would duplicate and possibly conflict with the theme.
- **Keybindings:** pick stable command IDs (`workbench.action.focusAuxiliaryBar`,
  `workbench.panel.chat.view.focus`, `terminal.new`) rather than extension-specific IDs.
  They're portable and don't break when an extension updates.
- **Repo is not a git repo yet.** When git is initialized, the project context files
  will already be in place and Hermes/Claude will start using them automatically.

## Verification

After writing:
1. `python` JSON parse on settings.json + keybindings.json (write_file auto-checks .json
   syntax already, but re-parse with verifier for shape)
2. `claude --version` and `hermes --version` to confirm both are present
3. Hand back to Akshay for visual confirmation:
   - Terminal panel opens Hermes automatically
   - ⌘L focuses Claude sidebar, ⌘I focuses terminal
   - Theme unchanged (warm amber still applied)
4. Reference existing per-project-environment-setup skill's verifier script if available,
   else ad-hoc shape check only

## What could go wrong (anticipated)

- **Hermes launcher resolution:** `hermes` must be on `$PATH` for the terminal profile
  to fire. If it's a Python venv binary (`~/.hermes/venv/bin/hermes`), use the full path.
- **Claude Code extension may bind ⌘L itself** (it actually does — Claude Code ships
  with `Claude Code: Focus on Chat View` bound to ⌘L on macOS). Our keybinding will
  override the default. That's intentional — user wants ⌘L → Claude.
- **macOS profile key.** Must be `osx`, not `macos` or `darwin`. Verified with VS Code
  schema docs.
