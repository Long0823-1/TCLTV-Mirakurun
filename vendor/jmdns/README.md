# JmDNS legacy-unicast compatibility patch

Source: https://raw.githubusercontent.com/jmdns/jmdns/v3.6.3/src/main/java/javax/jmdns/impl/SocketListener.java

Original source SHA-256: `e9c8de2b8ded9a601bc2e8a5251bdde4b600fc71f0c7aa06ea500c10b12dca43`. Apache-2.0; original license header and bundled JmDNS LICENSE/NOTICE retained.

The only logic change sends queries from a source port other than 5353 back to that sender, including standard IN questions without the QU bit. JmDNS 3.6.3 otherwise sends those answers to multicast port 5353, which Android one-shot resolver sockets do not receive. Queries from port 5353 retain upstream QU/QM behavior.

References: [RFC 6762 section 6.7](https://www.rfc-editor.org/rfc/rfc6762.html#section-6.7), [Android DNS Resolver](https://source.android.com/docs/core/ota/modular-system/dns-resolver).

The dependency download remains hash-pinned and unmodified. `scripts/patch-jmdns.py` builds this one class into a separate patched JAR used for D8. No runtime patching or extra root calls are involved.
