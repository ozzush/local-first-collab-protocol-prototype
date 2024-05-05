#!/bin/bash

# Check if correct number of arguments are provided
if [ "$#" -ne 6 ]; then
  echo "Usage: $0 duration num_clients update_frequency conflict_frac sending_delay result_file"
  exit 1
fi

# Extract arguments
duration=$1
num_clients=$2
update_frequency=$3
conflict_frac=$4
sending_delay=$5
result_file=$6

echo "Building server and client"
# Build the server and client projects
./gradlew :server:build > /dev/null 2>&1
./gradlew :client:build > /dev/null 2>&1

echo "Done!"

# Explain the benchmark
echo "Running benchmark for $duration seconds with $num_clients clients. Clients generate updates every $update_frequency seconds on average, with a sending delay of $sending_delay seconds. Of these updates, $conflict_frac are conflicts."

file_path=$PWD/$result_file

# Start the server in the background
./gradlew :server:run --args="--lifetime=$duration --stat-file=$file_path" &

# Save server process ID
server_pid=$!

# Wait for the server to start
echo "Waiting for the server to start..."
while ! curl -sSf http://localhost:9000/fetch > /dev/null 2>&1; do
  sleep 1
done
echo "Server is up and running."

# Define array to store client PIDs
declare -a client_pids=()

# Define function to start clients
start_clients() {
  local n=$1
  local frequency=$2
  local frac=$3
  local delay=$4
  for ((i=0; i<$n; i++)); do
    # Generate unique client name
    client_name="Client_$i"
    # Start client and save its PID
    ./gradlew :client:run --args="--name=$client_name --auto-update=$frequency --conflict-frac=$frac --network-delay=$delay" > /dev/null 2>&1 &
    client_pids+=($!)
  done
}

# Start clients
start_clients "$num_clients" "$update_frequency" "$conflict_frac" "$sending_delay"

# Wait for the server to stop
wait "$server_pid"

# Kill all client processes
for pid in "${client_pids[@]}"; do
  kill "$pid"
done

# Return server output
cat "$file_path"
