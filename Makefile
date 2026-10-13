.PHONY: dev start stop status logs

dev start:
	./dev.sh

stop:
	./dev.sh stop

status:
	./dev.sh status

logs:
	./dev.sh logs
