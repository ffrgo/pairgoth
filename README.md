# Pairgoth engine

## Sources structure

```
.
├── pairgoth.properties.example ............... Executable property file to instanciate
├── standalone.sh ............................. Executable run script, linux
├── standalone.bat............................. Executable run script, windows
├── server.sh.................................. Server debugging script, linux
├── client.sh.................................. Web client debugging script, linux
├── test.sh ................................... Executable test script, linux
├── application ............................... Executable final packaging
├── webserver ................................. Web container
├── docker .................................... Docker packaging
│   ├── pairgoth.properties.example ........... Docker property file to instanciate
│   └── run.sh ................................ Docker launch script
└── api-webapp ................................ Engine web application
    └── src
        ├── main
        │   ├── kotlin ........................ Engine kotlin sources (the real meat is here!)
        │   └── webapp ........................ Engine API webapp root
        │       └── WEB-INF ................... Engine API webapp configuration
        └── test
            └── kotlin ........................ Engine webapp API unit tests
└── view-webapp ............................... Web interface
```

## API Webapp sources structure

```
api-webapp/src/main/kotlin/org/jeudego/pairgoth
├── api .................................... API handlers
├── ext .................................... External: import/export features
├── model .................................. Domain logic model
├── pairing ................................ Pairing solvers
├── store .................................. Persistence handlers
├── util ................................... Various utilities
└── web .................................... Web interface
```

Tests are located in `webapp/src/test/kotlin`

## Building and running

### Executable

You need maven installed.

Copy and adapt `pairgoth.properties.example` towards `pairgoth.properties`, if needed. Otherwise pairgoth will use default properties (TODO - list them)

Just running `./standalone.sh` or `./standalone.bat` shoud build and run the engine and the view webapps.

### Docker

*docker container is not maintained for now - TODO*

Under windows, please use the WSL.

You need docker installed, and the current user being in the `docker` group.

Copy and adapt `docker/pairgoth.properties.example` towards `docker/pairgoth.properties`.

Just running `./run.sh` in the `docker` directory should build and run the engine.

## Debugging

The `./server.sh` will launch the server in debugging mode, with a remote debugger socket on port 5005.

The `./client.sh` will launch the web client in debugging mode, with a remote debugger socket on port 5006.



