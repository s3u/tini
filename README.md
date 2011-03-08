
# What is Tini

Tini is a async I/O HTTP client/server framework. You can use this to run HTTP clients, HTTP
servers, or even proxies. Tini is currently based on Java 7.

## Setup and Build

Set JAVA_HOME to point to a JDK7 installation, and add JAVA_HOME/bin to the path.

    mvn clean install

## Examples

    ./run.bash examples.HelloWorldServer # Hello world
    ./run.bash examples.EchoServer # Starts a echo server
    ./run.bash examples.ProxyServer # Starts a proxy server

## Client Example

    ./run.bash examples.AsyncClient # Starts a client