# Local secrets (gitignored)

Drop a development JWT signing key here to make tokens survive an
`auth-service` restart:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-dev.pem
```

Then in `infra/compose/.env`:

```
JWT_PRIVATE_KEY_FILE=file:/run/secrets/jwt-dev.pem
```

Without it the service generates a key at startup and says so in the log —
fine for a laptop, and the reason a restart signs everyone out. `*.pem` is
gitignored; nothing in here belongs in the repository.
