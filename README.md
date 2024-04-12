# Pairgoth

Welcome to Pairgoth, your Go Pairing Engine!

Pairgoth is a Go tournament pairing engine designed to make your tournament experience effortless. pairgoth is the successor of [opengotha](https://github.com/lucvannier/opengotha), the well known pairing system software developed by [Luc Vannier](http://vannier.info/jeux/accueil.htm) and uses the same algorithm internally, as well as import and export features towards its format.

Pairgoth supports the Swiss pairing system, ideal for championships with no handicap games, as well as the MacMahon pairing system, more suited for classical tournaments and cups. It is still in a beta stage. Future versions will support more pairing systems and more features. Your feedback is most welcome!

## How to use

Please refer to [Pairgoth](https://pairgoth.jeudego.org) landing page.

## Sources structure

```
.
├── pairgoth.properties.example ............... Executable property file to instanciate
├── standalone.sh ............................. Executable run script, linux
├── standalone.bat............................. Executable run script, windows
├── server.sh.................................. Server launch script, linux
├── client.sh.................................. Client launch script, linux
├── debug-server.sh............................ Server debugging script, linux
├── debug-client.sh............................ Client debugging script, linux
├── debug.bat.................................. Standalone debugging script, windows
├── test.sh ................................... Executable test script, linux
├── curl.sh ................................... API example script, linux
├── application ............................... Executable final packaging
├── webserver ................................. Web container
├── docker .................................... Docker packaging
│   ├── pairgoth.properties.example ........... Docker property file to instanciate
│   └── run.sh ................................ Docker launch script
├── pairgoth-common............................ Shared utility code library
└── api-webapp ................................ Engine web application
    └── src
        ├── main
        │   ├── kotlin ........................ Engine kotlin sources (the real meat is here!)
        │   └── webapp ........................ Engine API webapp root
        │       └── WEB-INF ................... Engine API webapp configuration
        └── test
            └── kotlin ........................ Engine webapp API unit tests
└── view-webapp ............................... View web application
```

## API Webapp sources structure

```
api-webapp/src/main/kotlin/org/jeudego/pairgoth
├── api .................................... API handlers
├── ext .................................... External: import/export features
├── model .................................. Domain logic model
├── pairing ................................ Pairing solvers
├── store .................................. Persistence handlers
└── web .................................... Web interface
```

Tests are located in `api-webapp/src/test/kotlin`

## Building and running

### Executable

You need maven installed.

Copy and adapt `pairgoth.properties.example` towards `pairgoth.properties`, if needed. Otherwise pairgoth will use default properties (TODO - list them)

Just running `./standalone.sh` or `./standalone.bat` shoud build and run the engine and the view webapps.

### Docker

Under windows, please use the WSL.

You need docker installed, and the current user being in the `docker` group.

Copy and adapt `docker/pairgoth.properties.example` towards `docker/pairgoth.properties`.

Just running `./run.sh` in the `docker` directory should build and run the engine.

## Debugging

The `./server.sh` will launch the server in debugging mode, with a remote debugger socket on port 5005.

The `./client.sh` will launch the web client in debugging mode, with a remote debugger socket on port 5006.

The corresponding `./debug-...` scripts will do the same with additional debugging features like automatic re-compilation of CSS files, automatic reloading of template files, etc.
