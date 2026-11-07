To run Pairgoth under docker:

- compile Pairgoth (`mvn package` at the repository root)
- optionally copy `../pairgoth.properties.example` to `pairgoth.properties` and adapt it to your needs
- launch `./run.sh` from within the `docker` directory: it copies a freshly built engine jar
  into `data/app` (the container's working directory) and starts the container

Tournament files and downloaded ratings live under `data/app`, so they survive container restarts.
