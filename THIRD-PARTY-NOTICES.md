# Third-Party Notices

This project is licensed under the Apache License, Version 2.0 (see [LICENSE](LICENSE)).
This file lists the third-party software included in the artifacts this project distributes —
the Spring Boot executable JAR produced by `:noone-server:bootJar`, the Docker images built from it,
and the web SPA bundled into it — together with the license under which each component is used.

Generated on 2026-11-07 from the resolved `runtimeClasspath` of `:noone-server` and the production
dependency closure of `noone-web`. Versions are those resolved at build time. Regenerate the lists
after dependency changes with:

```bash
./gradlew :noone-server:dependencies --configuration runtimeClasspath
cd noone-web && bun pm ls
```

License identifiers below are taken from each artifact's published POM or `package.json`.

> This document is provided for compliance purposes and is not legal advice.

## Components that need attention before redistribution

| Component | Version | License | Status |
| --- | --- | --- | --- |
| `com.github.zhkl0228:impersonator-okhttp` | 1.11.0 | LGPL-3.0 | Required for browser impersonation — see §1 |
| `com.github.zhkl0228:impersonator-bctls` | 1.11.0 | LGPL-3.0 | Transitive dependency of impersonator-okhttp — see §1 |
| `io.github.4ra1n:class-obf` | 1.10.1 | GPL-3.0 | Strong copyleft; arrives transitively via the memshellparty core — see §2 |

## 1. LGPL-3.0 components

### 1.1 `impersonator-okhttp` and `impersonator-bctls`

- **Coordinates:** `com.github.zhkl0228:impersonator-okhttp:1.11.0`,
  `com.github.zhkl0228:impersonator-bctls:1.11.0`
- **License:** GNU Lesser General Public License, version 3 (LGPL-3.0)
- **Copyright:** the impersonator project and its contributors; the forked OkHttp code is
  Copyright Square, Inc. (Apache-2.0) and the forked BouncyCastle code is Copyright the Legion
  of the Bouncy Castle Inc. (MIT).
- **Why it is here:** `impersonator-okhttp` replaces the official OkHttp artifact in
  `noone-transport` so that HTTP requests can present a browser-like TLS/JA3/JA4 and
  HTTP/2 fingerprint. `impersonator-bctls` is its TLS provider.
- **Upstream source:** <https://github.com/zhkl0228/impersonator>
- **Corresponding source for the shipped version** (the upstream repository has no `v1.11.0`
  tag or release, so the published sources JAR is the authoritative source):
  - <https://repo1.maven.org/maven2/com/github/zhkl0228/impersonator-okhttp/1.11.0/impersonator-okhttp-1.11.0-sources.jar>
  - <https://repo1.maven.org/maven2/com/github/zhkl0228/impersonator-bctls/1.11.0/impersonator-bctls-1.11.0-sources.jar>
- **Modifications:** none. Both artifacts are consumed unmodified as Maven dependencies.

LGPL-3.0 obligations and how this project meets them:

1. **Notice** — this file is shipped with the source tree and referenced from the README.
2. **License texts** — [licenses/LGPL-3.0.txt](licenses/LGPL-3.0.txt) and
   [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt) are included (LGPL-3.0 incorporates GPL-3.0 by
   reference).
3. **Source code** — the upstream repository and the exact corresponding sources JARs are linked
   above, so recipients can obtain, modify and rebuild the library.
4. **Replaceability** — the libraries are ordinary Maven dependencies resolved at build time.
   Anyone can substitute a modified `impersonator-okhttp` / `impersonator-bctls` JAR and rebuild
   the distribution with `./gradlew :noone-server:bootJar`, or place replacement JARs
   on the runtime classpath. If you modify either library, you must release your modified version
   of that library under LGPL-3.0; the rest of this project is unaffected.

Note: the upstream project declares all of its modules under LGPL-3.0 because its QUIC module
links other LGPL-3.0 code. This project uses only the `okhttp` and `bctls` modules, but treats
them as LGPL-3.0 because that is the license declared for the published artifacts.

## 2. GPL-3.0 component: `class-obf`

- **Coordinate:** `io.github.4ra1n:class-obf:1.10.1`
- **License:** GNU General Public License, version 3 (GPL-3.0)
- **Upstream source:** <https://github.com/4ra1n/class-obf>
- **License text:** [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)
- **Why it is here:** it is a transitive runtime dependency of
  `io.github.reajason:memshell-party-core`. No first-party source references it directly.

GPL-3.0 is a strong copyleft license with no library exception. A binary distribution that
includes `class-obf` must therefore be distributed in compliance with GPL-3.0, which in practice
means making the complete corresponding source of the combined work available under GPL-3.0.
Apache-2.0 is compatible with GPL-3.0, so this is legally possible, but it changes the effective
license of the distributed binary.

Before redistributing binaries, choose one of:

1. verify with the memshellparty upstream whether `class-obf` can be excluded or made optional,
   and exclude it in `noone-transport` / `noone-server` if the runtime does not need it;
2. obtain a permissive or commercial license from the `class-obf` author; or
3. distribute the combined work under GPL-3.0 and provide the corresponding source.

## 3. Java runtime dependencies

The tables below list every third-party artifact resolved on the runtime classpath of
`:noone-server`, grouped by the license declared in its POM. First-party modules
(`com.reajason.noone:noone-core`, `noone-transport`, `noone-server`) are Apache-2.0
and are omitted.

Where an artifact offers a choice of licenses, all alternatives are shown in the heading. This
project relies on the least restrictive alternative: Apache-2.0 for the `Apache-2.0 OR ...`
groups, EPL-2.0 for the `EPL-2.0 OR ...` groups, and CDDL for the `CDDL OR GPL-2.0` group.

#### LGPL-3.0 (2)

| Component | Version |
| --- | --- |
| `com.github.zhkl0228:impersonator-bctls` | 1.11.0 |
| `com.github.zhkl0228:impersonator-okhttp` | 1.11.0 |

#### GPL-3.0 (1)

| Component | Version |
| --- | --- |
| `io.github.4ra1n:class-obf` | 1.10.1 |

#### Apache-2.0 OR LGPL-2.1 (2)

| Component | Version |
| --- | --- |
| `net.java.dev.jna:jna` | 5.13.0 |
| `net.java.dev.jna:jna-platform` | 5.13.0 |

#### Apache-2.0 OR LGPL-2.1 OR MPL-1.1 (1)

| Component | Version |
| --- | --- |
| `org.javassist:javassist` | 3.30.2-GA |

#### Apache-2.0 OR EPL-1.0/EPL-2.0 (9)

| Component | Version |
| --- | --- |
| `org.eclipse.jetty:jetty-http` | 12.1.6 |
| `org.eclipse.jetty:jetty-io` | 12.1.6 |
| `org.eclipse.jetty:jetty-security` | 12.1.6 |
| `org.eclipse.jetty:jetty-server` | 12.1.6 |
| `org.eclipse.jetty:jetty-servlet` | 9.4.54.v20240208 |
| `org.eclipse.jetty:jetty-util` | 12.1.6 |
| `org.eclipse.jetty:jetty-util-ajax` | 12.1.6 |
| `org.mortbay.jetty:jetty` | 6.1.26 |
| `org.mortbay.jetty:jetty-util` | 6.1.26 |

#### EPL-2.0 OR LGPL-2.1 (2)

| Component | Version |
| --- | --- |
| `ch.qos.logback:logback-classic` | 1.5.32 |
| `ch.qos.logback:logback-core` | 1.5.32 |

#### EPL-2.0 OR GPL-2.0 with Classpath Exception (2)

| Component | Version |
| --- | --- |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 |
| `jakarta.transaction:jakarta.transaction-api` | 2.0.1 |

#### CDDL OR GPL-2.0 (3)

| Component | Version |
| --- | --- |
| `javax.annotation:javax.annotation-api` | 1.3.2 |
| `javax.servlet:javax.servlet-api` | 3.0.1 |
| `javax.websocket:javax.websocket-api` | 1.1 |

#### EPL-2.0 (1)

| Component | Version |
| --- | --- |
| `org.aspectj:aspectjweaver` | 1.9.25.1 |

#### Apache-2.0 (171)

| Component | Version |
| --- | --- |
| `com.alibaba.fastjson2:fastjson2` | 2.0.61 |
| `com.alibaba:dubbo` | 2.6.12 |
| `com.alibaba:dubbo-common` | 2.6.12 |
| `com.alibaba:dubbo-remoting-api` | 2.6.12 |
| `com.alibaba:dubbo-remoting-http` | 2.6.12 |
| `com.alibaba:dubbo-rpc-api` | 2.6.12 |
| `com.alibaba:dubbo-rpc-hessian` | 2.6.12 |
| `com.alibaba:dubbo-serialization-api` | 2.6.12 |
| `com.alibaba:fastjson` | 1.2.70 |
| `com.alibaba:hessian-lite` | 3.2.12 |
| `com.beust:jcommander` | 1.72 |
| `com.cedarsoftware:java-util` | 1.9.0 |
| `com.cedarsoftware:json-io` | 2.5.1 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.20 |
| `com.fasterxml.jackson.core:jackson-core` | 2.20.2 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.20.2 |
| `com.fasterxml:classmate` | 1.7.3 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.3 |
| `com.google.code.findbugs:jsr305` | 3.0.2 |
| `com.google.errorprone:error_prone_annotations` | 2.43.0 |
| `com.google.zxing:core` | 3.4.0 |
| `com.google.zxing:javase` | 3.4.0 |
| `com.squareup.okio:okio-jvm` | 3.18.1 |
| `com.zaxxer:HikariCP` | 7.0.2 |
| `commons-beanutils:commons-beanutils` | 1.9.2 |
| `commons-codec:commons-codec` | 1.19.0 |
| `commons-collections:commons-collections` | 3.2.1 |
| `commons-io:commons-io` | 2.15.1 |
| `commons-logging:commons-logging` | 1.3.5 |
| `de.javakaffee:kryo-serializers` | 0.42 |
| `de.ruedigermoeller:fst` | 2.48-jdk-6 |
| `io.airlift:aircompressor` | 2.0.3 |
| `io.jsonwebtoken:jjwt-api` | 0.13.0 |
| `io.jsonwebtoken:jjwt-impl` | 0.13.0 |
| `io.jsonwebtoken:jjwt-jackson` | 0.13.0 |
| `io.micrometer:micrometer-commons` | 1.16.3 |
| `io.micrometer:micrometer-observation` | 1.16.3 |
| `io.netty:netty-all` | 4.2.10.Final |
| `io.netty:netty-buffer` | 4.2.10.Final |
| `io.netty:netty-codec` | 4.2.10.Final |
| `io.netty:netty-codec-base` | 4.2.10.Final |
| `io.netty:netty-codec-classes-quic` | 4.2.10.Final |
| `io.netty:netty-codec-compression` | 4.2.10.Final |
| `io.netty:netty-codec-dns` | 4.2.10.Final |
| `io.netty:netty-codec-haproxy` | 4.2.10.Final |
| `io.netty:netty-codec-http` | 4.2.10.Final |
| `io.netty:netty-codec-http2` | 4.2.10.Final |
| `io.netty:netty-codec-http3` | 4.2.10.Final |
| `io.netty:netty-codec-marshalling` | 4.2.10.Final |
| `io.netty:netty-codec-memcache` | 4.2.10.Final |
| `io.netty:netty-codec-mqtt` | 4.2.10.Final |
| `io.netty:netty-codec-native-quic` | 4.2.10.Final |
| `io.netty:netty-codec-protobuf` | 4.2.10.Final |
| `io.netty:netty-codec-redis` | 4.2.10.Final |
| `io.netty:netty-codec-smtp` | 4.2.10.Final |
| `io.netty:netty-codec-socks` | 4.2.10.Final |
| `io.netty:netty-codec-stomp` | 4.2.10.Final |
| `io.netty:netty-codec-xml` | 4.2.10.Final |
| `io.netty:netty-common` | 4.2.10.Final |
| `io.netty:netty-handler` | 4.2.10.Final |
| `io.netty:netty-handler-proxy` | 4.2.10.Final |
| `io.netty:netty-handler-ssl-ocsp` | 4.2.10.Final |
| `io.netty:netty-resolver` | 4.2.10.Final |
| `io.netty:netty-resolver-dns` | 4.2.10.Final |
| `io.netty:netty-resolver-dns-classes-macos` | 4.2.10.Final |
| `io.netty:netty-resolver-dns-native-macos` | 4.2.10.Final |
| `io.netty:netty-transport` | 4.2.10.Final |
| `io.netty:netty-transport-classes-epoll` | 4.2.10.Final |
| `io.netty:netty-transport-classes-io_uring` | 4.2.10.Final |
| `io.netty:netty-transport-classes-kqueue` | 4.2.10.Final |
| `io.netty:netty-transport-native-epoll` | 4.2.10.Final |
| `io.netty:netty-transport-native-io_uring` | 4.2.10.Final |
| `io.netty:netty-transport-native-unix-common` | 4.2.10.Final |
| `io.netty:netty-transport-rxtx` | 4.2.10.Final |
| `io.netty:netty-transport-sctp` | 4.2.10.Final |
| `io.netty:netty-transport-udt` | 4.2.10.Final |
| `io.projectreactor.netty:reactor-netty-core` | 1.1.25 |
| `io.projectreactor:reactor-core` | 3.8.3 |
| `jakarta-regexp:jakarta-regexp` | 1.4 |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 |
| `log4j:log4j` | 1.2.16 |
| `net.bytebuddy:byte-buddy` | 1.18.7 |
| `nl.basjes.collections:prefixmap` | 2.1.0 |
| `nl.basjes.parse.useragent:yauaa` | 8.1.0 |
| `org.apache.bcel:bcel` | 5.2 |
| `org.apache.commons:commons-collections4` | 4.0 |
| `org.apache.commons:commons-lang3` | 3.20.0 |
| `org.apache.dubbo.extensions:dubbo-rpc-hessian` | 3.3.0 |
| `org.apache.dubbo.extensions:dubbo-serialization-native-hessian` | 3.3.0 |
| `org.apache.dubbo:dubbo` | 3.3.6 |
| `org.apache.dubbo:dubbo-common` | 3.3.0-beta.2 |
| `org.apache.dubbo:dubbo-remoting-api` | 3.3.0-beta.2 |
| `org.apache.dubbo:dubbo-remoting-http` | 3.3.0-beta.2 |
| `org.apache.dubbo:dubbo-rpc-api` | 3.3.0-beta.2 |
| `org.apache.dubbo:dubbo-serialization-api` | 3.3.0-beta.2 |
| `org.apache.dubbo:hessian-lite` | 4.0.4 |
| `org.apache.httpcomponents:httpclient` | 4.5.14 |
| `org.apache.httpcomponents:httpcore` | 4.4.16 |
| `org.apache.httpcomponents:httpcore-nio` | 4.4.16 |
| `org.apache.logging.log4j:log4j-api` | 2.25.3 |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.25.3 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.18 |
| `org.apache.tomcat.embed:tomcat-embed-el` | 11.0.18 |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 11.0.18 |
| `org.apache.tomcat:tomcat-annotations-api` | 11.0.18 |
| `org.hibernate.models:hibernate-models` | 1.0.1 |
| `org.hibernate.orm:hibernate-community-dialects` | 7.2.4.Final |
| `org.hibernate.orm:hibernate-core` | 7.2.4.Final |
| `org.hibernate.validator:hibernate-validator` | 9.0.1.Final |
| `org.jboss.logging:jboss-logging` | 3.6.2.Final |
| `org.jboss.netty:netty` | 3.2.5.Final |
| `org.jctools:jctools-core` | 4.0.5 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.21 |
| `org.jetbrains:annotations` | 26.0.2 |
| `org.jspecify:jspecify` | 1.0.0 |
| `org.mapstruct.extensions.spring:mapstruct-spring-annotations` | 2.0.0 |
| `org.mapstruct:mapstruct` | 1.6.3 |
| `org.objenesis:objenesis` | 2.5.1 |
| `org.springframework.boot:spring-boot` | 4.0.3 |
| `org.springframework.boot:spring-boot-autoconfigure` | 4.0.3 |
| `org.springframework.boot:spring-boot-data-commons` | 4.0.3 |
| `org.springframework.boot:spring-boot-data-jpa` | 4.0.3 |
| `org.springframework.boot:spring-boot-devtools` | 4.0.3 |
| `org.springframework.boot:spring-boot-hibernate` | 4.0.3 |
| `org.springframework.boot:spring-boot-http-converter` | 4.0.3 |
| `org.springframework.boot:spring-boot-jackson` | 4.0.3 |
| `org.springframework.boot:spring-boot-jdbc` | 4.0.3 |
| `org.springframework.boot:spring-boot-jpa` | 4.0.3 |
| `org.springframework.boot:spring-boot-persistence` | 4.0.3 |
| `org.springframework.boot:spring-boot-security` | 4.0.3 |
| `org.springframework.boot:spring-boot-servlet` | 4.0.3 |
| `org.springframework.boot:spring-boot-sql` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-aspectj` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-jackson` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-jdbc` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-logging` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-security` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-tomcat` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-tomcat-runtime` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-validation` | 4.0.3 |
| `org.springframework.boot:spring-boot-starter-web` | 4.0.3 |
| `org.springframework.boot:spring-boot-tomcat` | 4.0.3 |
| `org.springframework.boot:spring-boot-transaction` | 4.0.3 |
| `org.springframework.boot:spring-boot-validation` | 4.0.3 |
| `org.springframework.boot:spring-boot-web-server` | 4.0.3 |
| `org.springframework.boot:spring-boot-webmvc` | 4.0.3 |
| `org.springframework.data:spring-data-commons` | 4.0.3 |
| `org.springframework.data:spring-data-jpa` | 4.0.3 |
| `org.springframework.security:spring-security-config` | 7.0.3 |
| `org.springframework.security:spring-security-core` | 7.0.3 |
| `org.springframework.security:spring-security-crypto` | 7.0.3 |
| `org.springframework.security:spring-security-web` | 7.0.3 |
| `org.springframework:spring-aop` | 7.0.5 |
| `org.springframework:spring-aspects` | 7.0.5 |
| `org.springframework:spring-beans` | 7.0.5 |
| `org.springframework:spring-context` | 7.0.5 |
| `org.springframework:spring-core` | 7.0.5 |
| `org.springframework:spring-expression` | 7.0.5 |
| `org.springframework:spring-jdbc` | 7.0.5 |
| `org.springframework:spring-orm` | 7.0.5 |
| `org.springframework:spring-tx` | 7.0.5 |
| `org.springframework:spring-web` | 7.0.5 |
| `org.springframework:spring-webflux` | 7.0.5 |
| `org.springframework:spring-webmvc` | 7.0.5 |
| `org.xerial:sqlite-jdbc` | 3.50.3.0 |
| `org.yaml:snakeyaml` | 2.5 |
| `tools.jackson.core:jackson-core` | 3.0.4 |
| `tools.jackson.core:jackson-databind` | 3.0.4 |

#### MIT (Bouncy Castle Licence) (2)

| Component | Version |
| --- | --- |
| `org.bouncycastle:bcprov-jdk18on` | 1.86 |
| `org.bouncycastle:bcutil-jdk18on` | 1.86 |

#### MIT (25)

| Component | Version |
| --- | --- |
| `com.github.briandilley.jsonrpc4j:jsonrpc4j` | 1.7 |
| `dev.samstevens.totp:totp` | 1.7.1 |
| `io.github.reajason:memshell-party-api` | 3.1.0-SNAPSHOT |
| `io.github.reajason:memshell-party-common` | 3.0.0 |
| `io.github.reajason:memshell-party-core` | 3.1.0-SNAPSHOT |
| `io.github.reajason:memshell-party-packer` | 3.1.0-SNAPSHOT |
| `io.github.reajason:memshell-party-server-dubbo` | 3.0.0 |
| `io.github.reajason:memshell-party-server-servlet` | 3.1.0-SNAPSHOT |
| `io.github.reajason:memshell-party-server-spring-webflux` | 3.0.0 |
| `io.github.reajason:memshell-party-server-spring-webmvc` | 3.0.0 |
| `io.github.reajason:memshell-party-server-struts2` | 3.0.0 |
| `io.github.reajason:memshell-party-server-xxljob` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-antsword` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-behinder` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-command` | 3.1.0-SNAPSHOT |
| `io.github.reajason:memshell-party-shelltool-godzilla` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-neoregeorg` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-proxy` | 3.0.0 |
| `io.github.reajason:memshell-party-shelltool-suo5` | 3.0.0 |
| `io.github.reajason:memshell-party-stub-dubbo` | 3.0.0 |
| `io.github.reajason:memshell-party-stub-jetty` | 3.0.0 |
| `org.brotli:dec` | 0.1.2 |
| `org.checkerframework:checker-qual` | 3.52.0 |
| `org.slf4j:jul-to-slf4j` | 2.0.17 |
| `org.slf4j:slf4j-api` | 2.0.17 |

#### BSD-3-Clause (9)

| Component | Version |
| --- | --- |
| `com.esotericsoftware:kryo` | 4.0.1 |
| `com.esotericsoftware:minlog` | 1.3.0 |
| `com.esotericsoftware:reflectasm` | 1.11.3 |
| `com.github.jai-imageio:jai-imageio-core` | 1.4.0 |
| `com.google.protobuf:protobuf-java` | 3.25.8 |
| `org.antlr:antlr4-runtime` | 4.13.2 |
| `org.ow2.asm:asm` | 9.9 |
| `org.ow2.asm:asm-commons` | 9.9 |
| `org.ow2.asm:asm-tree` | 9.9 |

#### BSD-2-Clause (1)

| Component | Version |
| --- | --- |
| `org.postgresql:postgresql` | 42.7.10 |

#### EDL-1.0 (9)

| Component | Version |
| --- | --- |
| `com.sun.istack:istack-commons-runtime` | 4.1.2 |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 |
| `jakarta.jws:jakarta.jws-api` | 3.0.0 |
| `jakarta.persistence:jakarta.persistence-api` | 3.2.0 |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.4 |
| `org.eclipse.angus:angus-activation` | 2.0.3 |
| `org.glassfish.jaxb:jaxb-core` | 4.0.6 |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.6 |
| `org.glassfish.jaxb:txw2` | 4.0.6 |

#### MIT-0 (1)

| Component | Version |
| --- | --- |
| `org.reactivestreams:reactive-streams` | 1.0.4 |

#### Apache-1.1 (1)

| Component | Version |
| --- | --- |
| `com.caucho:hessian` | 4.0.66 |


## 4. Web production dependencies

The tables below list the production dependency closure of `noone-web` (200 packages), as
recorded in `bun.lock` and declared in each package's `package.json`. Dev-only tooling
(TypeScript, Vite, Vitest, oxlint/oxfmt, etc.) is not shipped in the SPA and is omitted.
#### MIT (179)

| Package | Version |
| --- | --- |
| `@babel/runtime` | 7.29.13 |
| `@base-ui/react` | 1.9.0 |
| `@base-ui/utils` | 0.5.0 |
| `@date-fns/tz` | 1.5.0 |
| `@dnd-kit/accessibility` | 3.1.1 |
| `@dnd-kit/core` | 6.3.1 |
| `@dnd-kit/modifiers` | 9.0.0 |
| `@dnd-kit/sortable` | 10.0.0 |
| `@dnd-kit/utilities` | 3.2.2 |
| `@floating-ui/core` | 1.8.0 |
| `@floating-ui/dom` | 1.8.0 |
| `@floating-ui/react-dom` | 2.1.9 |
| `@floating-ui/utils` | 0.2.12 |
| `@hookform/resolvers` | 7.0.2 |
| `@monaco-editor/loader` | 1.7.0 |
| `@monaco-editor/react` | 4.7.0 |
| `@radix-ui/number` | 1.1.3 |
| `@radix-ui/primitive` | 1.2.0 |
| `@radix-ui/react-accessible-icon` | 1.2.0 |
| `@radix-ui/react-accordion` | 1.3.1 |
| `@radix-ui/react-alert-dialog` | 1.2.1 |
| `@radix-ui/react-arrow` | 1.1.17 |
| `@radix-ui/react-aspect-ratio` | 1.2.0 |
| `@radix-ui/react-avatar` | 1.3.1 |
| `@radix-ui/react-checkbox` | 1.4.1 |
| `@radix-ui/react-collapsible` | 1.2.1 |
| `@radix-ui/react-collection` | 1.2.0 |
| `@radix-ui/react-compose-refs` | 1.1.5 |
| `@radix-ui/react-context` | 1.3.0 |
| `@radix-ui/react-context-menu` | 2.4.1 |
| `@radix-ui/react-dialog` | 1.3.1 |
| `@radix-ui/react-direction` | 1.2.0 |
| `@radix-ui/react-dismissable-layer` | 1.2.1 |
| `@radix-ui/react-dropdown-menu` | 2.2.1 |
| `@radix-ui/react-focus-guards` | 1.2.0 |
| `@radix-ui/react-focus-scope` | 1.3.1 |
| `@radix-ui/react-form` | 0.3.0 |
| `@radix-ui/react-hover-card` | 1.2.1 |
| `@radix-ui/react-id` | 1.1.4 |
| `@radix-ui/react-label` | 2.1.17 |
| `@radix-ui/react-menu` | 2.2.1 |
| `@radix-ui/react-menubar` | 1.2.1 |
| `@radix-ui/react-navigation-menu` | 1.4.1 |
| `@radix-ui/react-one-time-password-field` | 0.2.1 |
| `@radix-ui/react-password-toggle-field` | 0.2.1 |
| `@radix-ui/react-popover` | 1.3.1 |
| `@radix-ui/react-popper` | 1.4.0 |
| `@radix-ui/react-portal` | 1.2.0 |
| `@radix-ui/react-presence` | 1.1.11 |
| `@radix-ui/react-primitive` | 2.2.0 |
| `@radix-ui/react-progress` | 1.2.1 |
| `@radix-ui/react-radio-group` | 1.5.1 |
| `@radix-ui/react-roving-focus` | 1.2.1 |
| `@radix-ui/react-scroll-area` | 1.4.0 |
| `@radix-ui/react-select` | 2.4.1 |
| `@radix-ui/react-separator` | 1.2.0 |
| `@radix-ui/react-slider` | 1.6.1 |
| `@radix-ui/react-slot` | 1.5.0 |
| `@radix-ui/react-switch` | 1.4.1 |
| `@radix-ui/react-tabs` | 1.2.1 |
| `@radix-ui/react-toast` | 1.3.1 |
| `@radix-ui/react-toggle` | 1.2.1 |
| `@radix-ui/react-toggle-group` | 1.2.1 |
| `@radix-ui/react-toolbar` | 1.2.1 |
| `@radix-ui/react-tooltip` | 1.4.1 |
| `@radix-ui/react-use-callback-ref` | 1.1.5 |
| `@radix-ui/react-use-controllable-state` | 1.3.0 |
| `@radix-ui/react-use-effect-event` | 0.0.5 |
| `@radix-ui/react-use-escape-keydown` | 1.1.6 |
| `@radix-ui/react-use-is-hydrated` | 0.1.3 |
| `@radix-ui/react-use-layout-effect` | 1.1.4 |
| `@radix-ui/react-use-previous` | 1.1.4 |
| `@radix-ui/react-use-rect` | 1.1.4 |
| `@radix-ui/react-use-size` | 1.1.5 |
| `@radix-ui/react-visually-hidden` | 1.2.13 |
| `@radix-ui/rect` | 1.1.3 |
| `@reduxjs/toolkit` | 2.14.0 |
| `@remix-run/route-pattern` | 0.22.1 |
| `@standard-schema/spec` | 1.1.0 |
| `@standard-schema/utils` | 0.3.0 |
| `@tanstack/react-store` | 0.11.2 |
| `@tanstack/react-table` | 9.3.1 |
| `@tanstack/react-virtual` | 3.14.26 |
| `@tanstack/store` | 0.11.2 |
| `@tanstack/table-core` | 9.3.1 |
| `@tanstack/virtual-core` | 3.19.11 |
| `@types/d3-array` | 3.2.2 |
| `@types/d3-color` | 3.1.3 |
| `@types/d3-ease` | 3.0.2 |
| `@types/d3-interpolate` | 3.0.4 |
| `@types/d3-path` | 3.1.1 |
| `@types/d3-scale` | 4.0.9 |
| `@types/d3-shape` | 3.2.0 |
| `@types/d3-time` | 3.0.4 |
| `@types/d3-timer` | 3.0.2 |
| `@types/hast` | 3.0.5 |
| `@types/prismjs` | 1.26.6 |
| `@types/trusted-types` | 2.0.7 |
| `@types/unist` | 3.0.3 |
| `@types/use-sync-external-store` | 0.0.6 |
| `@xterm/addon-fit` | 0.11.0 |
| `@xterm/addon-search` | 0.16.0 |
| `@xterm/addon-unicode11` | 0.9.0 |
| `@xterm/addon-web-links` | 0.12.0 |
| `@xterm/xterm` | 6.0.0 |
| `aria-hidden` | 1.2.6 |
| `character-entities` | 2.0.2 |
| `character-entities-legacy` | 3.0.0 |
| `character-reference-invalid` | 2.0.1 |
| `clsx` | 2.1.1 |
| `cmdk` | 1.1.1 |
| `comma-separated-tokens` | 2.0.3 |
| `cookie-es` | 3.1.1 |
| `copy-to-clipboard` | 3.3.3 |
| `date-fns` | 4.4.0 |
| `dayjs` | 1.11.23 |
| `decimal.js-light` | 2.5.1 |
| `decode-named-character-reference` | 1.3.0 |
| `destr` | 2.0.5 |
| `detect-node-es` | 1.1.0 |
| `es-toolkit` | 1.53.1 |
| `eventemitter3` | 5.0.4 |
| `fault` | 1.0.4 |
| `format` | 0.2.2 |
| `get-nonce` | 1.0.1 |
| `hast-util-parse-selector` | 4.0.0 |
| `hastscript` | 9.0.3 |
| `immer` | 11.2.9 |
| `is-alphabetical` | 2.0.1 |
| `is-alphanumerical` | 2.0.1 |
| `is-decimal` | 2.0.1 |
| `is-hexadecimal` | 2.0.1 |
| `js-tokens` | 4.0.0 |
| `lodash.debounce` | 4.0.8 |
| `loose-envify` | 1.4.0 |
| `lowlight` | 1.20.0 |
| `marked` | 14.0.0 |
| `monaco-editor` | 0.57.0 |
| `next-themes` | 0.4.6 |
| `node-fetch-native` | 1.6.7 |
| `nuqs` | 2.10.2 |
| `object-assign` | 4.1.1 |
| `ofetch` | 1.5.1 |
| `parse-entities` | 4.0.2 |
| `prismjs` | 1.30.0 |
| `prop-types` | 15.8.1 |
| `property-information` | 7.2.0 |
| `radix-ui` | 1.8.1 |
| `react` | 19.3.1 |
| `react-copy-to-clipboard` | 5.1.1 |
| `react-day-picker` | 10.0.2 |
| `react-dom` | 19.3.1 |
| `react-hook-form` | 7.91.0 |
| `react-is` | 17.0.2 |
| `react-redux` | 9.3.1 |
| `react-remove-scroll` | 2.7.2 |
| `react-remove-scroll-bar` | 2.3.8 |
| `react-router` | 8.5.1 |
| `react-style-singleton` | 2.2.3 |
| `react-syntax-highlighter` | 16.1.2 |
| `recharts` | 3.10.1 |
| `redux` | 5.0.1 |
| `redux-thunk` | 3.1.0 |
| `refractor` | 5.0.0 |
| `reselect` | 5.2.0 |
| `scheduler` | 0.28.0 |
| `sonner` | 2.0.8 |
| `space-separated-tokens` | 2.0.2 |
| `state-local` | 1.0.7 |
| `tailwind-merge` | 3.7.0 |
| `tiny-invariant` | 1.3.3 |
| `toggle-selection` | 1.0.6 |
| `ufo` | 1.6.4 |
| `use-callback-ref` | 1.3.3 |
| `use-sidecar` | 1.1.3 |
| `use-sync-external-store` | 1.7.0 |
| `usehooks-ts` | 3.1.1 |
| `vaul` | 1.1.2 |
| `zod` | 4.6.7 |

#### ISC (12)

| Package | Version |
| --- | --- |
| `d3-array` | 3.2.5 |
| `d3-color` | 3.1.1 |
| `d3-format` | 3.1.3 |
| `d3-interpolate` | 3.0.2 |
| `d3-path` | 3.1.1 |
| `d3-scale` | 4.0.3 |
| `d3-shape` | 3.2.1 |
| `d3-time` | 3.1.1 |
| `d3-time-format` | 4.1.1 |
| `d3-timer` | 3.0.2 |
| `internmap` | 2.0.3 |
| `lucide-react` | 1.69.0 |

#### BSD-3-Clause (2)

| Package | Version |
| --- | --- |
| `d3-ease` | 3.0.2 |
| `highlight.js` | 10.7.3 |

#### MIT AND ISC AND BSD-3-Clause (1)

| Package | Version |
| --- | --- |
| `victory-vendor` | 37.4.1 |

#### Apache-2.0 (1)

| Package | Version |
| --- | --- |
| `class-variance-authority` | 0.7.1 |

#### (MPL-2.0 OR Apache-2.0) (1)

| Package | Version |
| --- | --- |
| `dompurify` | 3.4.15 |

#### CC0-1.0 (1)

| Package | Version |
| --- | --- |
| `highlightjs-vue` | 1.0.0 |

#### 0BSD (1)

| Package | Version |
| --- | --- |
| `tslib` | 2.9.1 |

#### Unlicense (1)

| Package | Version |
| --- | --- |
| `isbot` | 5.2.2 |

#### OFL-1.1 (1)

| Package | Version |
| --- | --- |
| `@fontsource-variable/inter` | 5.3.0 |


## 5. Source code and license texts

- Apache-2.0 (this project): [LICENSE](LICENSE)
- LGPL-3.0: [licenses/LGPL-3.0.txt](licenses/LGPL-3.0.txt)
- GPL-3.0: [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)
- SIL OFL-1.1 (Inter font): [licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)
- `impersonator-okhttp` / `impersonator-bctls` corresponding source: see §1
- `class-obf` source: <https://github.com/4ra1n/class-obf>
- All other Maven components are available, with their sources JARs, from Maven Central under the
  coordinates listed above. npm packages are available from the npm registry, and their license
  files are included in the published tarballs.

This list covers runtime dependencies only. Build-time tools are not part of the distributed
artifacts and are not listed. If you believe a component is missing, please open an issue.
