To run Pairgoth under docker:

- compile Pairgoth and copy `application/target/pairgoth-engine.jar` to `docker/data/app`
- copy `pairgoth.properties.example` to `docker/data/app/pairgoth.properties` and adapt it to your needs
- launch `./run.sh` from within the `docker` directory
