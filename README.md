# Explanation

Clients work on a shared log. Clients can append new entries to the log and share information
about their local updates through a centralized server. For each update the server decides whether 
it can be applied or not. In this example the server applies all updates 
except for the ones specifically marked by the client.

When a client is started, he connects to the server. After that he accepts arbitrary numbers 
as inputs, each input triggering an update. The numbers don't have any meaning except for numbers 0
and -1, but they must be unique. 0 means that this update should be rejected by the server, and -1
is reserved by the server to mark rejected updates. Getting an update rejected corrupts the client
and as of now the client can't be recovered.


# Ad-hoc testing the protocol

1. Create one server instance and two client instances in three different terminals

    ```bash
    ./gradlew :server:run
    ```
    ```bash
    ./gradlew :client:run --args="--name veronika" --console=plain
    ```
    ```bash
    ./gradlew :client:run --args="--name dmitrii" --console=plain
    ```

2. Cut off connection between the clients and the server

    ```bash
    sudo iptables -A OUTPUT -p tcp --dport 9001 -j DROP
    ```

3. Trigger a couple of updates on each of the client by inputting unique numbers. Uniqueness only
matters in the context of a single client. For example, you can input 5 and 6 on
both clients.

4. Restore connection to the server

    ```bash
    sudo iptables -F OUTPUT
    ```

    After a few seconds both clients should print a log that contain all the updates