#!/bin/sh
HOST=ec2-54-85-169-145.compute-1.amazonaws.com

echo Copy the server configuration to remote machine
scp -i EC2.pem ../service/tdmx-configuration.properties ec2-user@$HOST:tdmx-configuration.properties

echo Copy the server certificate to remote machine
scp -i EC2.pem ../service/server.keystore ec2-user@$HOST:server.keystore

echo Copy the server to remote machine
scp -i EC2.pem ../service/target/server.jar ec2-user@$HOST:server.jar

ssh -i EC2.pem ec2-user@$HOST 'bash -s' < cycle_server.sh
