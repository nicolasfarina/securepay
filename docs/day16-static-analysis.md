# Day 16 — Static analysis and quality gates

Implemented on October 17, 2026.

## Controls

Maven `verify` now runs SpotBugs with the OWASP Find Security Bugs detector plugin. The gate checks medium-confidence-or-higher findings in the security category and fails when it finds any. The same `mvn verify` command runs in the existing GitHub Actions job, so a pull request cannot pass the required `verify` status with a security finding.

The XML report is written to `target/spotbugsXml.xml` for local investigation. The include filter limits this gate to the security category; other bug categories are not treated as a security finding by this milestone.

## Reproduce

```powershell
mvn -B '-Duser.timezone=UTC' clean verify
```

Review `target/spotbugsXml.xml` when the build fails. Fix findings or document a narrowly scoped false positive filter with rationale; do not raise the allowed finding count to make the build pass.
