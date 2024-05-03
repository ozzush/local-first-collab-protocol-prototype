# Explanation

Clients work on a database, each client has a local copy of the database. Clients can make updates 
to the database and share information about their local updates through a centralized server. 
Each update contains a message.
For each update the server decides whether it is committed or not. 
In this example the server commits all updates that have matching baseId.

When a client is started, he connects to the server. After that he accepts input,
each line of input triggering an update. Inputting "conflict" or "c" has special meaning
if synchronization is triggered.

Sometimes a client's local database can diverge from the server's database. 
In this case the client starts a synchronization phase.
During synchronization phase the client sends all his unconfirmed updates to the server
together with the id of the last committed update he observed. The server checks whether applying
the updates would produce conflicts by checking whether 
any update contains a "conflict" or "c" message. If there are no conflicts, updates are committed,
ignoring the specified baseId. Finally, the server responds to the client with all the updates
he is missing, including the updates that were just committed.


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