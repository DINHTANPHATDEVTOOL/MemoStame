# MEMOSTAMP AUTONOMOUS FIX POLICY

## Allowed Without Human Approval

The agent may autonomously repair:

- compile errors
- Swift/Kotlin type mismatch
- missing imports
- syntax errors
- lint issues
- deterministic unit test failures
- straightforward nullability bugs
- incorrect DTO mapping
- CI script mistakes
- build configuration mistakes
- obvious regressions directly caused by current task

## Human Approval Required

Do not autonomously perform:

- destructive DB migration
- RLS removal
- auth architecture replacement
- payment architecture changes
- force push
- production data deletion
- secret rotation
- certificate/key replacement
- large dependency migration
- major architecture rewrite
- merging into protected main
