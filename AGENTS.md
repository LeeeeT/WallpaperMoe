Three rules here override default agent instincts:

## 1. Quality beats stability

This project has no external users and no compatibility obligations. Breaking changes, architecture changes, and new dependencies are highly encouraged whenever they improve quality in any way. Never preserve a worse design out of caution, and never leave redundancies behind: no back-compat routes, deprecated aliases, migration shims, or "old way still works" paths. When something is replaced, delete it.

## 2. Eliminate ambiguity before starting

Before starting each task, ask questions until the task is unambiguous (scope, intended behavior, edge cases, what may be broken or removed, etc). Don't fill gaps with guesses, and don't start implementing on an assumption the question would have killed.

## 3. Requests are approximate

When you hit a wall the wall is information: the design is wrong somewhere. STOP. Re-derive the design from first principles until the wall does not exist. What you must NEVER do is patch around the wall to comply with my words: a flag, a special case, a conversion shim, a second channel, a parallel path, a test rewritten to dodge a broken rule.

---

For anything release-related, read `RELEASING.md` first.
