# Day 20 — Software Bill of Materials

## What we learn

An SBOM is an inventory of a software product's components. SecurePay produces two complementary CycloneDX inventories:

- The CycloneDX Maven plugin managed by the Spring Boot parent lists application dependencies. Spring Boot places it at `META-INF/sbom/application.cdx.json` in the application classes and packages it into the executable JAR.
- Trivy inventories the final container image, including application libraries and operating-system packages from the runtime base image.

An SBOM is not a vulnerability scan or proof that components are safe. It gives scanners and responders a component-and-version inventory to compare against vulnerability advisories. The Day 19 Trivy image scan also covers operating-system packages from the container base image; the Maven SBOM describes Java dependencies.

## Implementation

- Declare the CycloneDX Maven plugin in `pom.xml` and inherit its compatible version and configuration from the Spring Boot parent.
- Keep Spring Boot's preconfigured Maven generation, avoiding a second execution that would duplicate or replace its artifact.
- Generate `target/securepay-image-sbom.cdx.json` from the same final image that Day 19 scans.
- Upload both inventories with test and static-analysis reports as the `securepay-verification-artifacts` CI artifact.

## Verification

Run `mvn -B -Duser.timezone=UTC verify`, then inspect `target/classes/META-INF/sbom/application.cdx.json` or the copy inside the executable JAR. CI also generates the image-level SBOM from `securepay:ci` and uploads both JSON files from each verification run.
