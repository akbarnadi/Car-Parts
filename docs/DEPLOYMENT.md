# Native server deployment on port 3005

The Ubuntu VPS runs three native processes:

| Component | Binding / name |
|---|---|
| Website | 0.0.0.0:3005 / car-parts-web.service |
| Java API | 127.0.0.1:13005 / car-parts-backend.service |
| PostgreSQL | existing native PostgreSQL 16 on localhost:5432 |
| App database and restricted login role | carparts_3005 |
| Release directory | /opt/car-parts/releases/<commit> |
| Active release | /opt/car-parts/current |
| Protected configuration | /etc/car-parts/backend.env and web.env |
| Database backups | /opt/car-parts/backups |

The frontend uses relative /api URLs. Next.js forwards API calls to the private backend; /health forwards to the database-aware Spring Boot health endpoint.

The release is built in GitHub Actions with Java 21 and Node.js 22. It includes the executable backend JAR, the Next.js standalone server, its static assets and deployment scripts. The server installs the Java 21 runtime if missing, then runs both services as the unprivileged carparts user.

Deployment uses the dedicated .github/workflows/deploy-car-parts-3005.yml workflow in akbarnadi/trade-manager, where the existing VPS_HOST, VPS_PORT, VPS_USER and VPS_SSH_KEY secrets are configured. Each deployment pins an exact Car-Parts commit. To deploy a subsequent version, update that workflow's checkout ref or launch the workflow with the desired ref.

The installer creates only the dedicated application database and role. It preserves the PostgreSQL service and other databases, stores credentials in root-only files, creates a database backup before migrations and enables automatic service startup after reboot. A failed health check restores the previous release when available; the database backup is retained for operator-controlled restoration.

## Initial manager

The username is manager0. Its initial password is randomly generated on the server and stored in /etc/car-parts/bootstrap-password. The deployment workflow provides an access-controlled initial-login artifact with a one-day retention period. No password is committed to either repository or printed in deployment logs.

Existing manager accounts and database credentials are retained when redeploying. Changing the manager's password in the application takes precedence over the initial bootstrap password.

## Operations

~~~bash
sudo systemctl status car-parts-web car-parts-backend
sudo journalctl -u car-parts-web -u car-parts-backend -n 100 --no-pager
curl -fsS http://127.0.0.1:3005/health
sudo -u postgres psql -d carparts_3005
~~~

Application services can be restarted independently:

~~~bash
sudo systemctl restart car-parts-backend car-parts-web
~~~

Production runtime does not use containers. Database lifecycle and administration use the native PostgreSQL service and psql. The application installer never removes or resets another application's data.
