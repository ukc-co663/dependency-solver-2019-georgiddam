all: compile

compile: deps
	./compile.sh

deps:
	./install.sh
	touch deps

test: compile
	./run_tests.sh

clean:
	rm -rf classes

reallyclean: clean
	rm -rf lib deps

.PHONY: all compile test clean reallyclean