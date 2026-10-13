# Native PostgreSQL development

Install and start PostgreSQL directly on your computer. The application requires PostgreSQL 16 or later, Java 21, Maven and Node.js 22.

For Ubuntu, install the database and create a development role/database:

~~~bash
sudo apt install postgresql postgresql-client
sudo systemctl enable --now postgresql
sudo -u postgres psql
~~~

In psql, choose a development password:

~~~sql
CREATE ROLE car_parts LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE car_parts OWNER car_parts;
~~~

Run ./dev.sh once to create the ignored .env.dev file, then configure DB_PASSWORD there. DB_URL, DB_HOST and DB_PORT can be changed for an existing native database. Only the first startup of an empty database creates manager0, using the randomly generated password in .env.dev.

~~~bash
./dev.sh
./dev.sh status
./dev.sh logs
./dev.sh stop
~~~

The website runs at http://localhost:3005. It proxies /api to the local Java API on port 8080. FRONTEND_PORT and BACKEND_PORT can be changed in .env.dev.

Ctrl+C and ./dev.sh stop stop the application processes. PostgreSQL remains managed independently by the operating system. Database data is preserved.

Flyway creates and upgrades the schema when Spring Boot starts. Database resets are a manual database-administration operation; the application runner does not delete databases.

For production deployment, see [DEPLOYMENT.md](DEPLOYMENT.md).
