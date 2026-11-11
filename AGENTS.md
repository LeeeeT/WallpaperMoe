# WallpaperMoe

## Rules

### 1. Select quality before stability

This repository has no compatibility requirements. Make a breaking change when
it gives the system a better design. Change the architecture or add a dependency
when this improves the result.

Do not keep an inferior design because a stable interface uses it. When a new
design replaces an old design, remove the old design completely. Do not keep
compatibility routes, deprecated names, migration shims, optional legacy modes,
or a second path that implements the old behavior.

### 2. Remove ambiguity before work starts

Make the task unambiguous before you change files. Ask questions
until each answer that can change the design or implementation is known.

Do not replace missing information with a guess. Do not start work with an
assumption when the user's answer can invalidate that work.

### 3. Derive the design from the objective

For every decision, ask what the best expert in that field would do and why they
would reject your current choice. If you can name that reason, don't make the
choice. Optimize for what that expert would judge correct, never for what
satisfies the stated constraints most cheaply.

### 4. Use controlled prose

Use ASD-STE100 in messages, commit messages, documentation, and code comments.
Use short sentences. Use active voice. Give one meaning to each sentence.
Avoid code comments. Use names and structure to explain the code.

