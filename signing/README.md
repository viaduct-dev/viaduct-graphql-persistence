# Release Signing Key

The public key used to verify release artifacts is stored at
`signing/viaduct-graphql-persistence-release.asc`.

Maven Central does not store this key directly. Publish it to a supported public keyserver:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys 94878551E804BB0421FEE32BBBE7C5EFFB31E7A8
```

Release identity:

- Primary UID: `Viaduct GraphQL Persistence Releases <maven@ductworks.io>`
- Fingerprint: `9487 8551 E804 BB04 21FE E32B BBE7 C5EF FB31 E7A8`
- Gradle key ID: `FB31E7A8`
- Expiration: August 19, 2028

The encrypted private key and its passphrase must never be committed. The GitHub Actions workflow
reads them from the `SIGNING_KEY`, `SIGNING_PASSWORD`, and `SIGNING_KEY_ID` repository secrets.
