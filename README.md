# Explanation

Clients work on a shared log. Clients can append new entries to the log and share information
about their local updates through a centralized server. For each update the server decides whether 
it is committed or not. In this example the server commits all updates 
that have matching baseId and non-empty id.

When a client is started, he connects to the server. After that he accepts input, 
each line of input triggering an update. Inputting "reject" generates an update with empty id,
which makes the server automatically reject it.


# Ad-hoc testing the protocol

1. Create one server instance and two client instances in three different terminals

    ```bash
    ./gradlew :server:run
    ```
    ```bash
    ./gradlew :client:run --args="--name veronika" --console=plain
    ```
    ```bash
    ./gradlew :client:run --args="--name dmitry" --console=plain
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

    After a few seconds both clients should print a consistent log.