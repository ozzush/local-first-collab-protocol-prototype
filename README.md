# Explanation

Clients work on a shared log. Clients can append new entries to the log and share information
about their local updates through a centralized server. For each update the server decides whether 
it is committed or not. In this example the server commits all updates 
that have matching baseId and non-empty id.

When a client is started, he connects to the server. After that he accepts input, 
each line of input triggering an update. Inputting "reject" or "r" generates an update with 
an id that starts with "-", which makes the server automatically reject it.


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

2. Trigger a couple of updates on each of the client by entering a newline. By default, the client
is set to wait 3 seconds before sending the update info to the server. Due to that two clients will
diverge if updates on both of them are triggered in the span of 3 seconds.