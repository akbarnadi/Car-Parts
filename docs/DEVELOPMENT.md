# Development runner

The repository includes a single-command development runner for macOS/Linux.

## First run

Requirements:

- Docker Desktop running
- Java 21
- Maven
- Node.js + npm

From the repository root:

```bash
./dev.sh
```

The script will:

1. verify required commands;
2. verify Docker Desktop is running;
3. create an ignored `.env.dev` with a generated local manager password when needed;
4. create `frontend/.env.local` from `.env.example` when needed;
5. start PostgreSQL with Docker Compose;
6. wait for PostgreSQL health;
7. run `npm ci` only when `frontend/node_modules` is missing;
8. start Spring Boot;
9. wait for `/actuator/health`;
10. start Next.js;
11. wait for the website to respond;
12. open `http://localhost:3000` automatically on macOS;
13. keep both application processes attached to the runner until `Ctrl+C`.

The generated local manager email/password are printed after startup and stored only in `.env.dev`, which is ignored by Git.

## Commands

```bash
./dev.sh              # start database + backend + frontend
./dev.sh stop         # stop frontend/backend and PostgreSQL
./dev.sh status       # show service state
./dev.sh logs         # follow backend/frontend log files
./dev.sh reset-db     # delete the development PostgreSQL volume (asks first)
```

Equivalent Make commands are available:

```bash
make dev
make stop
make status
make logs
make reset-db
```

## URLs

| Service | URL |
|---|---|
| Website | `http://localhost:3000` |
| Java API | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| PostgreSQL | `localhost:5432` |

## Logs

The runner writes application logs to:

```text
.dev/backend.log
.dev/frontend.log
```

Use:

```bash
make logs
```

to watch both.

## Stopping behavior

Pressing `Ctrl+C` during `./dev.sh` stops Next.js and Spring Boot but deliberately leaves PostgreSQL running so the next startup is faster and database data is preserved.

To stop everything:

```bash
./dev.sh stop
```

Stopping the database does **not** delete data.

## Resetting development data

When a clean database is needed:

```bash
./dev.sh reset-db
```

This removes the Docker PostgreSQL volume. The next `./dev.sh` run recreates the schema through Flyway migrations.

For scripts/CI where confirmation is not possible:

```bash
./dev.sh reset-db --yes
```

Never use the reset command against production infrastructure.

## Troubleshooting

If startup reports Docker is unavailable, open Docker Desktop and wait until the engine is running.

If port `3000` or `8080` is already occupied, run:

```bash
./dev.sh stop
```

If PostgreSQL cannot bind port `5432`, check whether a Homebrew PostgreSQL service is also running. The project development environment expects Docker PostgreSQL to own that port.

If backend startup fails, inspect:

```bash
tail -n 100 .dev/backend.log
```

If frontend startup fails, inspect:

```bash
tail -n 100 .dev/frontend.log
```

The project targets Java 21. The runner warns when another Java version is active.
