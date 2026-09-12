# Sample walkthrough

The sibling [sample-plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin) is a complete external consumer. Build the API with Java 21, then run `mvn -B -f sample-plugin/pom.xml clean verify`. Its workflow uses the API-free optional entry point and isolated runtime, discovers the service, checks capabilities, captures a scene, submits a job, polls progress, converts output, and handles typed failures.
