# How to Contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution;
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

## Community Guidelines

This project follows [Google's Open Source Community
Guidelines](https://opensource.google.com/conduct/).

## Developing

This project is divided into multiple packages, primarily:

- [`functions-framework-api`](./functions-framework-api) – The interfaces for functions.
- [`java-function-invoker`](./invoker)
  - `core` - The function invoker
  - `testfunction` - A set of test functions
  - `function-maven-plugin` - The Maven plugin for building functions
  - `conformance` - A set of functions used for conformance testing

### Setup JDK 11 / 17

Install JDK 11 and 17. One way to install these is through [SDK man](https://sdkman.io/).

```sh
sdk install java 11.0.2-open
sdk install java 17-open
sdk use java 17-open
sdk use java 11.0.2-open
```

Verify Java version with:

```sh
java --version
```

### Setup Apache Maven

Install `mvn`:

https://maven.apache.org/install.html

### Install and Run Invoker Tests Locally

```
cd invoker;
mvn test;
```

### Running Conformance Tests Locally

First, install Go 1.16+, then run the conformance tests with this script:

```
./run_conformance_tests.sh
```