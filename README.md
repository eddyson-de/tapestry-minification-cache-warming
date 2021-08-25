# tapestry-minification-cache-warming [![status](https://github.com/eddyson-de/tapestry-minification-cache-warming/actions/workflows/main.yml/badge.svg)](https://github.com/eddyson-de/tapestry-minification-cache-warming/actions/workflows/main.yml)

[![Join the chat at https://gitter.im/eddyson-de/tapestry-minification-cache-warming](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/eddyson-de/tapestry-minification-cache-warming?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
Control which resources are pre-minified during the application's startup.

Tapestry can minify resources like JavaScript modules and CSS stylesheets during runtime. This is done lazily when the resources are first requested. When the minification process becomes complex, this can take a considerable amount of time, slowing down the first requests' processing.

With this module, resources can be added to a cache warming phase. This makes the application startup take a little longer but will help to answer all requests equally fast.

## Usage

A basic usage example to override Tapestry's shipped jQuery library with a newer version.

### `build.gradle`:
```groovy
respositories {
  maven { url "https://jitpack.io" }
}

dependencies {
  implementation 'com.github.eddyson-de:tapestry-minification-cache-warming:0.5.0'
}

```

### Application Module:
```java
@Contribute(MinificationCacheWarming.class)
public static void configureMinificationCacheWarming(
    final OrderedConfiguration<StackExtension> configuration) {
  configuration.add("stack:core", StackExtension.stack("core"));
  configuration.add("module:bootstrap/modal", StackExtension.module("bootstrap/modal"));
  configuration.add("stylesheet:tapestry.css",
    StackExtension.stylesheet("${tapestry.asset.root}/tapestry.css"));
}
```
