# Pairgoth engine

## Sources structure

.
├── pairgoth.properties.example ............... Executable property file to instanciate
├── debug.sh .................................. Executable debug script, linux
├── debug.bat ................................. Executable debug script, windows
├── run.sh .................................... Executable run script, linux
├── run.bat ................................... Executable run script, windows
├── test.sh ................................... Executable test script, linux
├── application ............................... Executable final packaging
├── bootstrap ................................. Executable entry point
├── container ................................. Web container
├── docker .................................... Docker packaging
│   ├── pairgoth.properties.example ........... Docker property file to instanciate
│   └── run.sh ................................ Docker launch script
└── webapp .................................... Engine web application
    └── src
        ├── main
        │   ├── kotlin ........................ Engine kotlin sources (the real meat is here!)
        │   └── webapp ........................ Engine API webapp root
        │       └── WEB-INF ................... Engine API webapp configuration
        └── test
            └── kotlin ........................ Engine webapp API unit tests

## Building


### Executable

You need maven installed.

Copy and adapt `pairgoth.properties.example` towards `pairgoth.properties`.

Just running `./run.sh` or `./run.bat` shoud build and run the engine .

### Docker

Under windows, please use the WSL.

You need docker installed, and the current user being in the `docker` group.

Copy and adapt `docker/pairgoth.properties.example` towards `docker/pairgoth.properties`.

Just running `./run.sh` in the `docker` directory should build and run the engine.

