.PHONY: dev start stop status logs reset-db

dev start:
	./dev.sh

stop:
	./dev.sh stop

status:
	./dev.sh status

logs:
	./dev.sh logs

reset-db:
	./dev.sh reset-db
