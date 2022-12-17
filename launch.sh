#!/usr/bin/env sh
echo "Please, input path to Dockerfile"
read pathToDockerfile
echo "Please, input path to origin .csv file"
read originCsvFilePath
echo "Please, input path to desired destination of output .csv file"
read desiredDestinationCsvFilePath
echo "Building Docker image"
docker build -f $pathToDockerfile -t quarkus/xmas-jvm .
echo "Running Docker container. Warning! Via Windows Git Bash it won't work'"
docker run -d quarkus/xmas-jvm
echo "Getting Docker container ID"
CONTAINER_ID=$(docker ps -q)
echo "Copying origin .csv file to the container with id $CONTAINER_ID"
docker cp $originCsvFilePath $CONTAINER_ID:/app/origin.csv
echo "OK"
echo "Executing Jar"
docker exec -it $CONTAINER_ID java -jar /app/quarkus-run.jar /app/origin.csv /app/output.csv
echo "Done"
echo "Copying output .csv file to the desired destination outside the container with id $CONTAINER_ID"
docker cp $CONTAINER_ID:/app/output.csv $desiredDestinationCsvFilePath
echo "Done! Stopping Docker container with id $CONTAINER_ID"
docker stop $CONTAINER_ID
read
