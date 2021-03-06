## TDMX
![TDMX Logo](https://raw.githubusercontent.com/TDMX/tdmx/master/wiki/images/www/tdmx.png)
This project is the work-in-progress development of **Trusted Domain Messaging eXchange**. TDMX is a specification enabling secure B2B messaging between separate corporations via cloud service providers.

Visit [http://tdmx.org](http://tdmx.org "tdmx.org") and the specification section below for more information.

Anyone wishing to contribute get more information, contact [Peter Klauser](https://github.com/pjklauser "Peter Klauser").
## Specification
 - [Introduction](https://github.com/TDMX/tdmx/blob/master/wiki/Introduction.md)
  - [Summary](https://github.com/TDMX/tdmx/blob/master/wiki/Introduction.md#summary)
  - [Features](https://github.com/TDMX/tdmx/blob/master/wiki/Introduction.md#features)
  - [What TDMX is NOT](https://github.com/TDMX/tdmx/blob/master/wiki/Introduction.md#what-tdmx-is-not)
  - [Motivation](https://github.com/TDMX/tdmx/blob/master/wiki/Motivation.md#motivation)
 - [High Level Concepts](https://github.com/TDMX/tdmx/blob/master/wiki/HighLevelConcepts.md#high-level-concepts)
  - [System Architecture](https://github.com/TDMX/tdmx/blob/master/wiki/HighLevelConcepts.md#system-architecture)
  - [DNS Model](https://github.com/TDMX/tdmx/blob/master/wiki/HighLevelConcepts.md#dns-model)
  - [Trust Relationships](https://github.com/TDMX/tdmx/blob/master/wiki/HighLevelConcepts.md#trust-relationships)
  - [Cryptography](https://github.com/TDMX/tdmx/blob/master/wiki/Cryptography.md#cryptography)
  - [Crypto Scheme](https://github.com/TDMX/tdmx/blob/master/wiki/Cryptography.md#crypt-scheme)
  - [Concrete Schemes](https://github.com/TDMX/tdmx/blob/master/wiki/Cryptography.md#concrete-schemes)
  - [Crypto Scheme Support Matrix](https://github.com/TDMX/tdmx/blob/master/wiki/Cryptography.md#crypto-scheme-support-matrix)


## Development Setup
clone the tdmx Github repository
in Eclipse, use the `import existing maven projects` wizard on the tdmx root directory. Prerequisite is the m2eclipse plugin which can be found via Eclipse marketplace, or is bundled with Jboss Tools plugins. 

To build run `mvn clean install` in the root tdmx folder.

To conform with developer coding guidelines, you can set the Java Editor's "Save Actions" to perform formatting on each source folder. The template for formatting and cleanup are stored under tdmx/format.xml and tdmx/cleanup.xml respectively.

## Folders
The root of the tdmx repository structure contains...

- core : common utility classes and services
- admin-console : a Web-Gui application to administer service providers and manage cryptographic credentials and trust relationships.
- jetty-war-launcher : classes to run the admin-console stand-alone given the console.war
- service : the ServiceProvider's WebServices implementations.

## Run
to run the admin-console `mvn jetty:run` in the admin-console folder. The UI is then availible at:

[http://localhost:8080/admin](http://localhost:8080/admin)

to run the service provider run the  `org.tdmx.server.runtime.ServerLauncher` class as a Java Application in the service folder. The server is then reachable via

[https://localhost:8443/api/](https://localhost:8443/api/)



  
  