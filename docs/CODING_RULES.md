# Coding Rules

## Java Version
- Use **Java 21** features whenever possible.
  - Use `var` for local variable type inference.
  - Use **Pattern Matching** for `instanceof` and switch expressions.
  - Use **Text Blocks** for multi-line strings.
  - Use **Records** for immutable data carriers.

## Immutability
- Mark all local variables, method parameters, and class fields as `final` unless they strictly require mutability.

## Comments
- All comments must be in **English**.
- Comments should be appropriate and concise, explaining *why* rather than *what* (unless the *what* is complex).
- Unit tests should have clear English comments explaining the test case.

## Formatting
- Follow standard Java conventions.
- Organize imports.
