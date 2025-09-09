#!/bin/bash

# Script to deploy our stack to localstack

set -e # Stops the script if any command fails

aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
    --stack-name patient-management

aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file "./cdk.out/localstack.template.json"

# queires the LB and outputs the address, does not require for deployement, just for referance, once deplyed we have to use this url for making http requests, and it will keep changing everytime
aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" --output text