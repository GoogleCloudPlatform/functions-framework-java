# Changelog

## [1.3.0](https://github.com/GoogleCloudPlatform/functions-framework-java/compare/java-function-invoker-v1.2.1...java-function-invoker-v1.3.0) (2023-06-01)


### Features

* Define strongly typed function interface ([#186](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/186)) ([5264e35](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/5264e35b2522a789d65f0e0fd9bb5584694529eb))


### Bug Fixes

* bump org.eclipse.jetty dependency to 9.4.51 ([#201](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/201)) ([0102c8f](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/0102c8f543280ff5ba5727508f87083a9f54ef74))

## [1.2.1](https://github.com/GoogleCloudPlatform/functions-framework-java/compare/java-function-invoker-v1.2.0...java-function-invoker-v1.2.1) (2023-03-02)


### Bug Fixes

* retrieving http headers on request object should be case insenstive ([#178](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/178)) ([44da871](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/44da871e06e967ce132bea06c3b7c5d1b06ddd6b))

## [1.2.0](https://github.com/GoogleCloudPlatform/functions-framework-java/compare/java-function-invoker-v1.1.1...java-function-invoker-v1.2.0) (2022-10-05)


### Features

* allow to stop the invoker ([#128](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/128)) ([14908ca](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/14908caa9e5be824dfb74fff3a3234c4bce688e7))
* enable converting CloudEvent requests to Background Event requests ([#123](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/123)) ([1c4a014](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/1c4a01470cc4ee7b3de3c3d7ae4af24e47eb2810))
* Increase maximum concurrent requests for jetty server to 1000.  ([#144](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/144)) ([439d0b5](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/439d0b5d77b2f765e65d84e7d5f31399e547d004))


### Bug Fixes

* Add build env vars support for function deployment. ([#133](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/133)) ([0e052f3](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/0e052f376231192278061ec79bcf9d710ec310f4))
* bump dependency versions ([#134](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/134)) ([faff79d](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/faff79d16c6df178d66f0185fb78fba003e60745))
* bump jetty version to 9.4.49.v20220914 ([#164](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/164)) ([f5231a2](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/f5231a2303aa3565b29d494936e40ee1ec78fdbb))
* make user function exceptions log level SEVERE ([#113](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/113)) ([1684c0e](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/1684c0ef55dc33f2c4c7f7514d99b0e7af75c44f))
* update conformance tests ([#108](https://github.com/GoogleCloudPlatform/functions-framework-java/issues/108)) ([72852d0](https://github.com/GoogleCloudPlatform/functions-framework-java/commit/72852d0f23cdaed48569245440dcd1533c8c7563))
